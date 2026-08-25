package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.adapter.out.persistence.DocumentRepository;
import com.danycb.findocAnalyzer.features.vault.adapter.out.vector.PgVectorIndexAdapter;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.FilingSectionsPort;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.danycb.findocAnalyzer.infra.config.VectorConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({
        DocumentRepository.class,
        AnalyzeDocumentService.class,
        VaultAuditLogger.class,
        AnalyzeDocumentServicePostgresIT.TestConfig.class
})
class AnalyzeDocumentServicePostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AnalyzeDocumentService service;

    @Autowired
    private DocumentRepository repository;

    @Autowired
    private PgVectorIndexAdapter vectorIndex;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unresolvedAmendmentCompletesAfterOriginalLookupFlushesProcessingState() throws Exception {
        UUID teamId = UUID.randomUUID();
        Document document = new TransactionTemplate(transactionManager).execute(status -> repository.save(
                Document.builder()
                        .teamId(teamId)
                        .fileName("AAPL 10-K/A")
                        .status(DocumentStatus.PENDING)
                        .source(DocumentSource.EDGAR)
                        .ticker("AAPL")
                        .formType("10-K/A")
                        .baseFormType("10-K")
                        .amendment(true)
                        .accessionNumber("amendment")
                        .amendsAccessionNumber("missing-original")
                        .amendmentLinkStatus(AmendmentLinkStatus.UNRESOLVED)
                        .searchable(true)
                        .build()));

        Throwable analysisFailure = catchThrowable(() -> service.analyze(document.getId(), null));

        StoredState stored = storedState(document.getId());
        List<String> vectors = vectorTexts(document.getId());
        assertAll(
                () -> assertThat(analysisFailure).isNull(),
                () -> assertThat(stored.status).isEqualTo("COMPLETED"),
                () -> assertThat(stored.amendsAccession).isEqualTo("new-original"),
                () -> assertThat(stored.amendmentLinkStatus).isEqualTo("UNRESOLVED"),
                () -> assertThat(stored.searchable).isTrue(),
                () -> assertThat(vectors).containsExactly("new vector text"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void vectorSqlFailureRollsBackAnalysisThenPersistsFailedInSeparateTransaction() throws Exception {
        UUID teamId = UUID.randomUUID();
        LocalDate committedFilingDate = LocalDate.of(2025, 1, 2);
        LocalDate committedReportDate = LocalDate.of(2024, 9, 28);
        Document document = new TransactionTemplate(transactionManager).execute(status -> repository.save(
                Document.builder()
                        .teamId(teamId)
                        .fileName("AAPL 10-K/A")
                        .status(DocumentStatus.PENDING)
                        .source(DocumentSource.EDGAR)
                        .ticker("AAPL")
                        .formType("10-K/A")
                        .baseFormType("10-K")
                        .amendment(true)
                        .accessionNumber("amendment")
                        .amendsAccessionNumber("committed-original")
                        .amendmentLinkStatus(AmendmentLinkStatus.UNRESOLVED)
                        .filingDate(committedFilingDate)
                        .reportDate(committedReportDate)
                        .searchable(true)
                        .build()));
        vectorIndex.ingest(
                List.of(new ParsedSection(1, "Item 1A", "Risks", "last good vector text")), document);
        installFailingVectorTrigger();

        Throwable analysisFailure;
        try {
            analysisFailure = catchThrowable(() -> service.analyze(document.getId(), null));
        } finally {
            removeFailingVectorTrigger();
        }

        StoredState stored = storedState(document.getId());
        List<String> vectors = vectorTexts(document.getId());
        assertAll(
                () -> assertThat(analysisFailure).isNull(),
                () -> assertThat(stored.status).isEqualTo("FAILED"),
                () -> assertThat(stored.amendsAccession).isEqualTo("committed-original"),
                () -> assertThat(stored.filingDate).isEqualTo(committedFilingDate),
                () -> assertThat(stored.reportDate).isEqualTo(committedReportDate),
                () -> assertThat(stored.searchable).isTrue(),
                () -> assertThat(vectors).containsExactly("last good vector text"));
    }

    private void installFailingVectorTrigger() throws Exception {
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION fail_new_analysis_vector() RETURNS trigger AS $$
                    BEGIN
                        IF NEW.text = 'new vector text' THEN
                            RAISE EXCEPTION 'forced vector SQL failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE TRIGGER fail_new_analysis_vector_trigger
                    BEFORE INSERT ON document_embeddings
                    FOR EACH ROW EXECUTE FUNCTION fail_new_analysis_vector()
                    """);
        }
    }

    private void removeFailingVectorTrigger() throws Exception {
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS fail_new_analysis_vector_trigger ON document_embeddings");
            statement.execute("DROP FUNCTION IF EXISTS fail_new_analysis_vector()");
        }
    }

    private StoredState storedState(UUID documentId) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT status, amends_accession_number, amendment_link_status,
                            filing_date, report_date, searchable
                     FROM document_metadata WHERE id = ?
                     """)) {
            statement.setObject(1, documentId);
            try (var result = statement.executeQuery()) {
                result.next();
                return new StoredState(
                        result.getString("status"),
                        result.getString("amends_accession_number"),
                        result.getString("amendment_link_status"),
                        result.getObject("filing_date", LocalDate.class),
                        result.getObject("report_date", LocalDate.class),
                        result.getBoolean("searchable"));
            }
        }
    }

    private List<String> vectorTexts(UUID documentId) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT text FROM document_embeddings WHERE document_id = ? ORDER BY chunk_index")) {
            statement.setObject(1, documentId);
            try (var result = statement.executeQuery()) {
                var texts = new java.util.ArrayList<String>();
                while (result.next()) {
                    texts.add(result.getString("text"));
                }
                return texts;
            }
        }
    }

    private record StoredState(
            String status,
            String amendsAccession,
            String amendmentLinkStatus,
            LocalDate filingDate,
            LocalDate reportDate,
            boolean searchable) {
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        EmbeddingModel deterministicEmbeddingModel() {
            return segments -> {
                float[] vector = new float[384];
                vector[0] = 1.0f;
                return Response.from(segments.stream()
                        .map(segment -> Embedding.from(vector.clone()))
                        .toList());
            };
        }

        @Bean
        PgVectorIndexAdapter vectorIndex(DataSource dataSource, EmbeddingModel embeddingModel) {
            var store = new VectorConfig().embeddingStore(dataSource);
            return new PgVectorIndexAdapter(embeddingModel, store, store);
        }

        @Bean
        FilingSectionsPort filingSections() {
            return (ticker, accession) -> new FilingSectionsResult(
                    accession,
                    "new-original",
                    "10-K/A",
                    LocalDate.of(2025, 2, 3),
                    LocalDate.of(2024, 12, 31),
                    true,
                    List.of(new ParsedSection(1, "Item 1A", "Risks", "new vector text")));
        }

        @Bean
        ExternalStoragePort storage() {
            return new ExternalStoragePort() {
                @Override public String generateUploadUrl(UUID id, String type, long length) { return "upload"; }
                @Override public String generateViewUrl(UUID id) { return "view"; }
                @Override public byte[] download(String key) { return new byte[0]; }
                @Override public void delete(UUID id) { }
                @Override public String buildObjectKey(UUID id) { return "files/" + id; }
            };
        }

        @Bean
        DocumentParserPort parser() {
            return (content, fileName, contentType) -> List.of();
        }
    }
}
