package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.infra.config.VectorConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers integration test for {@link PgVectorIndexAdapter}: ingestion against the real
 * langchain4j pgvector store (built from the production {@link VectorConfig}) on a pgvector Postgres.
 * A deterministic fake {@link EmbeddingModel} stands in for the ONNX model so the test asserts on the
 * persisted rows and metadata, not on embedding values. Verifies section-to-row mapping, oversized
 * section chunking, blank-section skipping, and document-scoped deletion.
 */
@Testcontainers(disabledWithoutDocker = true)
class PgVectorIndexAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie").asCompatibleSubstituteFor("postgres"));

    private static DataSource dataSource;
    private static EmbeddingStore<TextSegment> store;
    private static DocumentVectorPersistence vectorPersistence;

    private PgVectorIndexAdapter adapter;

    @BeforeAll
    static void migrate() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).load().migrate();
        store = new VectorConfig().embeddingStore(ds);
        vectorPersistence = (DocumentVectorPersistence) store;
    }

    @BeforeEach
    void cleanEmbeddings() throws Exception {
        adapter = new PgVectorIndexAdapter(new ConstantEmbeddingModel(), store, vectorPersistence);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM document_embeddings");
            s.execute("DELETE FROM document_metadata");
        }
    }

    @Test
    void ingestPersistsOneRowPerShortSection() throws Exception {
        UUID docId = insertParentDocument();
        UUID teamId = UUID.randomUUID();

        adapter.ingest(List.of(
                new ParsedSection(1, "Item 1", "Overview", "A short overview section."),
                new ParsedSection(2, "Item 1A", "Risks", "A short risks section.")
        ), uploadDocument(docId, teamId, "report.pdf"));

        List<Row> rows = rowsForDocument(docId);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.teamId).isEqualTo(teamId.toString());
            assertThat(r.fileName).isEqualTo("report.pdf");
            assertThat(r.chunkIndex).isZero();
            assertThat(r.effective).isTrue();
        });
        assertThat(rows).extracting(r -> r.sectionTitle).containsExactlyInAnyOrder("Overview", "Risks");
        assertThat(rows).extracting(r -> r.page).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void ingestSplitsOversizedSectionIntoMultipleChunks() throws Exception {
        UUID docId = insertParentDocument();
        String longText = ("The quarterly revenue analysis continued in great detail. ").repeat(40);
        assertThat(longText.length()).isGreaterThan(900);

        adapter.ingest(List.of(new ParsedSection(1, "Item 7", "MD&A", longText)),
                uploadDocument(docId, UUID.randomUUID(), "big.pdf"));

        List<Row> rows = rowsForDocument(docId);
        assertThat(rows).hasSizeGreaterThan(1);
        assertThat(rows).extracting(r -> r.chunkIndex).contains(0, 1);
        // The full section text is preserved on every child chunk for citation display.
        assertThat(rows).allSatisfy(r -> assertThat(r.sectionText).isEqualTo(longText));
    }

    @Test
    void ingestSkipsBlankSections() throws Exception {
        UUID docId = insertParentDocument();

        adapter.ingest(List.of(
                new ParsedSection(1, "Item 1", "Empty", "   "),
                new ParsedSection(2, "Item 2", "Null", null),
                new ParsedSection(3, "Item 3", "Real", "Actual content.")
        ), uploadDocument(docId, UUID.randomUUID(), "sparse.pdf"));

        List<Row> rows = rowsForDocument(docId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().sectionTitle).isEqualTo("Real");
    }

    @Test
    void deleteByDocumentIdRemovesOnlyThatDocumentsVectors() throws Exception {
        UUID keep = insertParentDocument();
        UUID remove = insertParentDocument();
        adapter.ingest(List.of(new ParsedSection(1, "K", "K", "keep me")),
                uploadDocument(keep, UUID.randomUUID(), "keep.pdf"));
        adapter.ingest(List.of(new ParsedSection(1, "R", "R", "remove me")),
                uploadDocument(remove, UUID.randomUUID(), "remove.pdf"));

        adapter.deleteByDocumentId(remove);

        assertThat(rowsForDocument(remove)).isEmpty();
        assertThat(rowsForDocument(keep)).hasSize(1);
    }

    @Test
    void amendmentSupersedesOnlyMatchingItemAndRetainsHistoricalVectors() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID originalId = insertParentDocument();
        UUID amendmentId = insertParentDocument();
        Document original = edgarDocument(originalId, teamId, "10-K", "original", null,
                LocalDate.of(2024, 11, 1));
        Document amendment = edgarDocument(amendmentId, teamId, "10-K/A", "amendment", "original",
                LocalDate.of(2025, 1, 2));

        adapter.ingest(List.of(
                new ParsedSection(1, "Item 1", "Business", "original business"),
                new ParsedSection(2, "Item 1A", "Risks", "original risks")
        ), original);
        adapter.ingest(List.of(
                new ParsedSection(2, "Item 1A", "Risks", "amended risks")
        ), amendment);

        List<Row> rows = rowsForFamily(teamId, "original");
        assertThat(rows).hasSize(3);
        assertThat(rows).filteredOn(r -> r.sectionItem.equals("Item 1"))
                .singleElement().satisfies(r -> assertThat(r.effective).isTrue());
        assertThat(rows).filteredOn(r -> r.sectionItem.equals("Item 1A") && r.accession.equals("original"))
                .singleElement().satisfies(r -> assertThat(r.effective).isFalse());
        assertThat(rows).filteredOn(r -> r.sectionItem.equals("Item 1A") && r.accession.equals("amendment"))
                .singleElement().satisfies(r -> assertThat(r.effective).isTrue());
    }

    @Test
    void laterAmendmentSupersedesEarlierAmendmentForSameItem() throws Exception {
        UUID teamId = UUID.randomUUID();
        Document original = edgarDocument(insertParentDocument(), teamId, "10-K", "original", null,
                LocalDate.of(2024, 11, 1));
        Document firstAmendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment-1", "original",
                LocalDate.of(2025, 1, 2));
        Document secondAmendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment-2", "original",
                LocalDate.of(2025, 1, 3));

        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "original risks")), original);
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "second amended risks")), secondAmendment);
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "first amended risks")), firstAmendment);

        List<Row> rows = rowsForFamily(teamId, "original");
        assertThat(rows).hasSize(3);
        assertThat(rows).filteredOn(r -> r.effective)
                .singleElement().satisfies(r -> assertThat(r.accession).isEqualTo("amendment-2"));
    }

    @Test
    void deletingLatestAmendmentRestoresPreviousEffectiveItemVersion() throws Exception {
        UUID teamId = UUID.randomUUID();
        Document original = edgarDocument(insertParentDocument(), teamId, "10-K", "original", null,
                LocalDate.of(2024, 11, 1));
        Document firstAmendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment-1", "original",
                LocalDate.of(2025, 1, 2));
        Document secondAmendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment-2", "original",
                LocalDate.of(2025, 1, 3));
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "original risks")), original);
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "first amended risks")), firstAmendment);
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "second amended risks")), secondAmendment);

        adapter.deleteByDocumentId(secondAmendment.getId());

        assertThat(rowsForFamily(teamId, "original")).filteredOn(row -> row.effective)
                .singleElement().satisfies(row -> assertThat(row.accession).isEqualTo("amendment-1"));
    }

    @Test
    void reanalysisRestoresPriorFamilyWinnerWhenAnAmendedItemDisappears() throws Exception {
        UUID teamId = UUID.randomUUID();
        Document original = edgarDocument(insertParentDocument(), teamId, "10-K", "original", null,
                LocalDate.of(2024, 11, 1));
        Document amendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment", "original",
                LocalDate.of(2025, 1, 2));
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "original risks")), original);
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "amended risks")), amendment);

        adapter.ingest(List.of(new ParsedSection(2, "Item 7", "MD&A", "new amendment item")), amendment);

        List<Row> rows = rowsForFamily(teamId, "original");
        assertThat(rows).filteredOn(row -> row.sectionItem.equals("Item 1A") && row.effective)
                .singleElement().satisfies(row -> assertThat(row.accession).isEqualTo("original"));
        assertThat(rows).filteredOn(row -> row.sectionItem.equals("Item 7") && row.effective)
                .singleElement().satisfies(row -> assertThat(row.accession).isEqualTo("amendment"));
    }

    @Test
    void reanalysisRecomputesBothOldAndNewFamiliesWhenAmendmentReferenceChanges() throws Exception {
        UUID teamId = UUID.randomUUID();
        Document firstOriginal = edgarDocument(insertParentDocument(), teamId, "10-K", "original-a", null,
                LocalDate.of(2024, 11, 1));
        Document secondOriginal = edgarDocument(insertParentDocument(), teamId, "10-K", "original-b", null,
                LocalDate.of(2024, 11, 2));
        UUID amendmentId = insertParentDocument();
        Document initialAmendment = edgarDocument(amendmentId, teamId, "10-K/A", "amendment", "original-a",
                LocalDate.of(2025, 1, 2));
        Document movedAmendment = edgarDocument(amendmentId, teamId, "10-K/A", "amendment", "original-b",
                LocalDate.of(2025, 1, 2));
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "first original")), firstOriginal);
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "second original")), secondOriginal);
        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "amendment for A")), initialAmendment);

        adapter.ingest(List.of(new ParsedSection(1, "Item 1A", "Risks", "amendment moved to B")), movedAmendment);

        assertThat(rowsForFamily(teamId, "original-a")).filteredOn(row -> row.effective)
                .singleElement().satisfies(row -> assertThat(row.accession).isEqualTo("original-a"));
        assertThat(rowsForFamily(teamId, "original-b")).filteredOn(row -> row.effective)
                .singleElement().satisfies(row -> assertThat(row.accession).isEqualTo("amendment"));
    }

    @Test
    void vectorReplacementRollsBackWithSurroundingDatabaseTransaction() throws Exception {
        UUID documentId = insertParentDocument();
        Document document = uploadDocument(documentId, UUID.randomUUID(), "report.pdf");
        adapter.ingest(List.of(new ParsedSection(1, "Item 7", "MD&A", "last committed text")), document);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            adapter.ingest(List.of(new ParsedSection(1, "Item 7", "MD&A", "uncommitted replacement")), document);
            throw new IllegalStateException("later metadata persistence failed");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("later metadata persistence failed");

        assertThat(rowsForDocument(documentId)).singleElement().satisfies(row ->
                assertThat(row.text).isEqualTo("last committed text"));
    }

    @Test
    void vectorDeletionRollsBackWithSurroundingDatabaseTransaction() throws Exception {
        UUID documentId = insertParentDocument();
        Document document = uploadDocument(documentId, UUID.randomUUID(), "report.pdf");
        adapter.ingest(List.of(new ParsedSection(1, "Item 7", "MD&A", "retained text")), document);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            adapter.deleteByDocumentId(documentId);
            throw new IllegalStateException("later metadata deletion failed");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("later metadata deletion failed");

        assertThat(rowsForDocument(documentId)).singleElement().satisfies(row ->
                assertThat(row.text).isEqualTo("retained text"));
    }

    @Test
    void concurrentOutOfOrderAmendmentsLeaveExactlyOneEffectiveVersion() throws Exception {
        UUID teamId = UUID.randomUUID();
        Document firstAmendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment-1", "original",
                LocalDate.of(2025, 1, 2));
        Document secondAmendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment-2", "original",
                LocalDate.of(2025, 1, 3));
        CountDownLatch secondCompleted = new CountDownLatch(1);
        PgVectorIndexAdapter concurrentAdapter = new PgVectorIndexAdapter(
                new OutOfOrderEmbeddingModel(secondCompleted), store, vectorPersistence);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentAdapter.ingest(
                    List.of(new ParsedSection(1, "Item 1A", "Risks", "first amendment")), firstAmendment));
            var second = executor.submit(() -> {
                concurrentAdapter.ingest(
                        List.of(new ParsedSection(1, "Item 1A", "Risks", "second amendment")), secondAmendment);
                secondCompleted.countDown();
            });
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertThat(rowsForFamily(teamId, "original")).filteredOn(row -> row.effective)
                .singleElement().satisfies(row -> assertThat(row.accession).isEqualTo("amendment-2"));
    }

    @Test
    void failedMultiItemSupersessionLeavesAllOlderSectionsEffective() throws Exception {
        UUID teamId = UUID.randomUUID();
        Document original = edgarDocument(insertParentDocument(), teamId, "10-K", "original", null,
                LocalDate.of(2024, 11, 1));
        Document amendment = edgarDocument(insertParentDocument(), teamId, "10-K/A", "amendment", "original",
                LocalDate.of(2025, 1, 2));

        adapter.ingest(List.of(
                new ParsedSection(1, "Item 1A", "Risks", "original risks"),
                new ParsedSection(2, "Item 7", "MD&A", "original management discussion")
        ), original);

        installFailureOnItem7Supersession();
        try {
            assertThatThrownBy(() -> adapter.ingest(List.of(
                    new ParsedSection(1, "Item 1A", "Risks", "amended risks"),
                    new ParsedSection(2, "Item 7", "MD&A", "amended management discussion")
            ), amendment)).isInstanceOf(IllegalStateException.class);

            List<Row> rows = rowsForFamily(teamId, "original");
            assertThat(rows).hasSize(2);
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.accession).isEqualTo("original");
                assertThat(row.effective).isTrue();
            });
        } finally {
            removeSupersessionFailureTrigger();
        }
    }

    // ---- JDBC helpers -----------------------------------------------------------------------

    private UUID insertParentDocument() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO document_metadata (id, file_name, uploaded_at, status, source) "
                             + "VALUES (?, ?, now(), 'PENDING', 'UPLOAD')")) {
            ps.setObject(1, id);
            ps.setString(2, "parent.pdf");
            ps.executeUpdate();
        }
        return id;
    }

    private void installFailureOnItem7Supersession() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION fail_item_7_supersession() RETURNS trigger AS $$
                    BEGIN
                        IF OLD.section_item = 'Item 7' AND NEW.effective = 'false' THEN
                            RAISE EXCEPTION 'forced supersession failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE TRIGGER fail_item_7_supersession_trigger
                    BEFORE UPDATE ON document_embeddings
                    FOR EACH ROW EXECUTE FUNCTION fail_item_7_supersession()
                    """);
        }
    }

    private void removeSupersessionFailureTrigger() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS fail_item_7_supersession_trigger ON document_embeddings");
            statement.execute("DROP FUNCTION IF EXISTS fail_item_7_supersession()");
        }
    }

    private List<Row> rowsForDocument(UUID docId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT team_id, file_name, page, chunk_index, section_title, section_text, text, "
                             + "section_item, accession_number, effective "
                             + "FROM document_embeddings WHERE document_id = ? ORDER BY chunk_index")) {
            ps.setObject(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Row> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    Row r = new Row();
                    r.teamId = rs.getString("team_id");
                    r.fileName = rs.getString("file_name");
                    r.page = rs.getInt("page");
                    r.chunkIndex = rs.getInt("chunk_index");
                    r.sectionTitle = rs.getString("section_title");
                    r.sectionText = rs.getString("section_text");
                    r.text = rs.getString("text");
                    r.sectionItem = rs.getString("section_item");
                    r.accession = rs.getString("accession_number");
                    r.effective = rs.getBoolean("effective");
                    rows.add(r);
                }
                return rows;
            }
        }
    }

    private List<Row> rowsForFamily(UUID teamId, String originalAccession) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT document_id, team_id, file_name, page, chunk_index, section_title, section_text, "
                             + "text, section_item, accession_number, effective FROM document_embeddings "
                             + "WHERE team_id = ? AND original_accession_number = ? ORDER BY accession_number")) {
            ps.setObject(1, teamId);
            ps.setString(2, originalAccession);
            try (ResultSet rs = ps.executeQuery()) {
                List<Row> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    Row r = new Row();
                    r.teamId = rs.getString("team_id");
                    r.fileName = rs.getString("file_name");
                    r.page = rs.getInt("page");
                    r.chunkIndex = rs.getInt("chunk_index");
                    r.sectionTitle = rs.getString("section_title");
                    r.sectionText = rs.getString("section_text");
                    r.text = rs.getString("text");
                    r.sectionItem = rs.getString("section_item");
                    r.accession = rs.getString("accession_number");
                    r.effective = rs.getBoolean("effective");
                    rows.add(r);
                }
                return rows;
            }
        }
    }

    private Document uploadDocument(UUID id, UUID teamId, String fileName) {
        return Document.builder().id(id).teamId(teamId).fileName(fileName).source(DocumentSource.UPLOAD).build();
    }

    private Document edgarDocument(UUID id, UUID teamId, String form, String accession,
                                   String amendsAccession, LocalDate filingDate) {
        return Document.builder()
                .id(id)
                .teamId(teamId)
                .fileName("AAPL " + form)
                .source(DocumentSource.EDGAR)
                .formType(form)
                .baseFormType(form.replace("/A", ""))
                .amendment(form.endsWith("/A"))
                .accessionNumber(accession)
                .amendsAccessionNumber(amendsAccession)
                .filingDate(filingDate)
                .build();
    }

    private static final class Row {
        String teamId;
        String fileName;
        int page;
        int chunkIndex;
        String sectionTitle;
        String sectionText;
        String text;
        String sectionItem;
        String accession;
        boolean effective;
    }

    // ---- fake -------------------------------------------------------------------------------

    /** Deterministic model: every segment maps to the same 384-dim unit vector. */
    static final class ConstantEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            float[] vector = new float[384];
            vector[0] = 1.0f;
            List<Embedding> embeddings = segments.stream().map(s -> Embedding.from(vector.clone())).toList();
            return Response.from(embeddings);
        }
    }

    static final class OutOfOrderEmbeddingModel implements EmbeddingModel {
        private final CountDownLatch secondCompleted;

        OutOfOrderEmbeddingModel(CountDownLatch secondCompleted) {
            this.secondCompleted = secondCompleted;
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            if (segments.getFirst().text().contains("first")) {
                try {
                    if (!secondCompleted.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("newer amendment did not complete");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            float[] vector = new float[384];
            vector[0] = 1.0f;
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(vector.clone()))
                    .toList());
        }
    }
}
