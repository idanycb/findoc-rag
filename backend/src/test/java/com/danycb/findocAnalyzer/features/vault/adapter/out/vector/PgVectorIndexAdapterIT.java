package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    private final PgVectorIndexAdapter adapter = new PgVectorIndexAdapter(new ConstantEmbeddingModel(), store);

    @BeforeAll
    static void migrate() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).load().migrate();
        store = new VectorConfig().embeddingStore(ds);
    }

    @BeforeEach
    void cleanEmbeddings() throws Exception {
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
                new ParsedSection(1, "Overview", "A short overview section."),
                new ParsedSection(2, "Risks", "A short risks section.")
        ), docId, teamId, "report.pdf");

        List<Row> rows = rowsForDocument(docId);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.teamId).isEqualTo(teamId.toString());
            assertThat(r.fileName).isEqualTo("report.pdf");
            assertThat(r.chunkIndex).isZero();
        });
        assertThat(rows).extracting(r -> r.sectionTitle).containsExactlyInAnyOrder("Overview", "Risks");
        assertThat(rows).extracting(r -> r.page).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void ingestSplitsOversizedSectionIntoMultipleChunks() throws Exception {
        UUID docId = insertParentDocument();
        String longText = ("The quarterly revenue analysis continued in great detail. ").repeat(40);
        assertThat(longText.length()).isGreaterThan(900);

        adapter.ingest(List.of(new ParsedSection(1, "MD&A", longText)), docId, UUID.randomUUID(), "big.pdf");

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
                new ParsedSection(1, "Empty", "   "),
                new ParsedSection(2, "Null", null),
                new ParsedSection(3, "Real", "Actual content.")
        ), docId, UUID.randomUUID(), "sparse.pdf");

        List<Row> rows = rowsForDocument(docId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().sectionTitle).isEqualTo("Real");
    }

    @Test
    void deleteByDocumentIdRemovesOnlyThatDocumentsVectors() throws Exception {
        UUID keep = insertParentDocument();
        UUID remove = insertParentDocument();
        adapter.ingest(List.of(new ParsedSection(1, "K", "keep me")), keep, UUID.randomUUID(), "keep.pdf");
        adapter.ingest(List.of(new ParsedSection(1, "R", "remove me")), remove, UUID.randomUUID(), "remove.pdf");

        adapter.deleteByDocumentId(remove);

        assertThat(rowsForDocument(remove)).isEmpty();
        assertThat(rowsForDocument(keep)).hasSize(1);
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

    private List<Row> rowsForDocument(UUID docId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT team_id, file_name, page, chunk_index, section_title, section_text, text "
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
                    rows.add(r);
                }
                return rows;
            }
        }
    }

    private static final class Row {
        String teamId;
        String fileName;
        int page;
        int chunkIndex;
        String sectionTitle;
        String sectionText;
        String text;
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
}
