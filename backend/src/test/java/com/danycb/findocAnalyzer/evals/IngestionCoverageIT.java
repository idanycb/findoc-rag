package com.danycb.findocAnalyzer.evals;

import com.danycb.findocAnalyzer.features.vault.adapter.out.edgar.FixtureFilingSectionsAdapter;
import com.danycb.findocAnalyzer.features.vault.adapter.out.vector.DocumentVectorPersistence;
import com.danycb.findocAnalyzer.features.vault.adapter.out.vector.PgVectorIndexAdapter;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;
import com.danycb.findocAnalyzer.infra.config.VectorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("eval")
@Testcontainers(disabledWithoutDocker = true)
class IngestionCoverageIT {
    private static final String ORIGINAL = "0001628280-25-003063";
    private static final String AMENDMENT = "0001104659-25-042659";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static DataSource dataSource;
    private static PgVectorIndexAdapter index;
    private static FixtureFilingSectionsAdapter fixtures;

    @BeforeAll
    static void setUp() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        Flyway.configure().dataSource(source).load().migrate();
        EmbeddingStore<TextSegment> store = new VectorConfig().embeddingStore(source);
        index = new PgVectorIndexAdapter(new ConstantEmbeddingModel(), store, (DocumentVectorPersistence) store);
        fixtures = new FixtureFilingSectionsAdapter(JSON,
                Path.of(System.getProperty("findoc.eval.root", "../evals"), "corpus", "tesla-2025"));
    }

    @Test
    void fixtureCorpusIngestsEveryExpectedSectionWithStableMetadataAndOffsets() throws Exception {
        JsonNode corpusManifest = EvaluationInputs.readJson(JSON, "corpus/tesla-2025/manifest.json");
        EvaluationInputs.verifyCorpusManifest(corpusManifest, "corpus/tesla-2025");
        JsonNode ingestionManifest = EvaluationInputs.readJson(JSON, "datasets/tesla-2025-v1.ingestion.json");
        UUID teamId = UUID.randomUUID();
        Map<String, Document> documents = new HashMap<>();

        for (JsonNode expected : ingestionManifest.path("filings")) {
            String accession = expected.path("accessionNumber").asText();
            FilingSectionsResult filing = fixtures.fetchSections("TSLA", accession);
            assertThat(filing.hasSearchableSections()).isEqualTo(expected.path("hasSearchableSections").asBoolean());
            assertThat(filing.amendsAccessionNumber()).isEqualTo(textOrNull(expected.path("amendsAccessionNumber")));
            assertThat(filing.sections()).allSatisfy(section -> {
                assertThat(section.item()).isNotBlank();
                assertThat(section.text()).isNotBlank();
                assertThat(section.pageNumber()).as("EDGAR must not fabricate page provenance").isNull();
            });

            Document document = document(teamId, filing);
            insertParent(document);
            index.ingest(filing.sections(), document);
            documents.put(accession, document);

            Set<String> expectedSections = new HashSet<>();
            expected.path("expectedSections").forEach(node -> expectedSections.add(node.asText()));
            assertThat(sectionsFor(accession)).containsExactlyInAnyOrderElementsOf(expectedSections);
            assertThat(rowsFor(accession)).allSatisfy(row -> {
                assertThat(row.formType()).isEqualTo(expected.path("form").asText());
                assertThat(row.filingDate()).isEqualTo(expected.path("filingDate").asText());
                assertThat(row.originalAccession()).isEqualTo(
                        accession.equals(AMENDMENT) ? ORIGINAL : accession);
                assertThat(row.page()).isNull();
                assertThat(row.chunkStart()).isNotNegative();
            });
        }

        assertThat(sectionsFor(AMENDMENT)).contains("Explanatory Note");
        List<Row> before = allRows();
        for (String accession : List.of(ORIGINAL, AMENDMENT)) {
            index.ingest(fixtures.fetchSections("TSLA", accession).sections(), documents.get(accession));
        }
        assertThat(allRows()).containsExactlyElementsOf(before);
    }

    private static Document document(UUID teamId, FilingSectionsResult filing) {
        return Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName("TSLA " + filing.formType())
                .uploadedAt(Instant.now())
                .source(DocumentSource.EDGAR)
                .ticker("TSLA")
                .formType(filing.formType())
                .baseFormType(filing.formType().replace("/A", ""))
                .amendment(filing.formType().endsWith("/A"))
                .accessionNumber(filing.accessionNumber())
                .amendsAccessionNumber(filing.amendsAccessionNumber())
                .filingDate(filing.filingDate())
                .reportDate(filing.reportDate())
                .build();
    }

    private static void insertParent(Document document) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO document_metadata
                    (id, file_name, uploaded_at, status, team_id, ticker, form_type, report_date, filing_date,
                     accession_number, source, base_form_type, is_amendment, amends_accession_number,
                     amendment_link_status, searchable)
                VALUES (?, ?, now(), 'COMPLETED', ?, 'TSLA', ?, ?, ?, ?, 'EDGAR', ?, ?, ?, ?, true)
                """)) {
            statement.setObject(1, document.getId());
            statement.setString(2, document.getFileName());
            statement.setObject(3, document.getTeamId());
            statement.setString(4, document.getFormType());
            statement.setObject(5, document.getReportDate());
            statement.setObject(6, document.getFilingDate());
            statement.setString(7, document.getAccessionNumber());
            statement.setString(8, document.getBaseFormType());
            statement.setBoolean(9, document.isAmendment());
            statement.setString(10, document.getAmendsAccessionNumber());
            statement.setString(11, document.isAmendment() ? "LINKED" : "NOT_APPLICABLE");
            statement.executeUpdate();
        }
    }

    private static Set<String> sectionsFor(String accession) throws Exception {
        return new HashSet<>(rowsFor(accession).stream().map(Row::sectionItem).toList());
    }

    private static List<Row> rowsFor(String accession) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT accession_number, original_accession_number, form_type, filing_date, section_item,
                       chunk_index, chunk_start, text, page
                FROM document_embeddings WHERE accession_number = ?
                ORDER BY section_item, chunk_index, text
                """)) {
            statement.setString(1, accession);
            try (ResultSet result = statement.executeQuery()) {
                List<Row> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(new Row(
                            result.getString("accession_number"),
                            result.getString("original_accession_number"),
                            result.getString("form_type"),
                            result.getString("filing_date"),
                            result.getString("section_item"),
                            result.getInt("chunk_index"),
                            result.getInt("chunk_start"),
                            result.getString("text"),
                            result.getObject("page", Integer.class)));
                }
                return rows;
            }
        }
    }

    private static List<Row> allRows() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.addAll(rowsFor(AMENDMENT));
        rows.addAll(rowsFor(ORIGINAL));
        return rows;
    }

    private static String textOrNull(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.asText();
    }

    private record Row(String accession, String originalAccession, String formType, String filingDate,
                       String sectionItem, int chunkIndex, int chunkStart, String text, Integer page) {
    }

    private static final class ConstantEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = segments.stream().map(ignored -> {
                float[] vector = new float[384];
                vector[0] = 1.0f;
                return Embedding.from(vector);
            }).toList();
            return Response.from(embeddings);
        }
    }
}
