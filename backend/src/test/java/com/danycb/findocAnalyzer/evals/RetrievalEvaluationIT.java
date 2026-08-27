package com.danycb.findocAnalyzer.evals;

import com.danycb.findocAnalyzer.features.chat.adapter.out.vector.PgVectorSearchAdapter;
import com.danycb.findocAnalyzer.features.chat.adapter.out.vector.RetrievalProperties;
import com.danycb.findocAnalyzer.features.chat.adapter.out.llm.LangChain4jAiService;
import com.danycb.findocAnalyzer.features.chat.adapter.out.llm.LangChain4jLlmAdapter;
import com.danycb.findocAnalyzer.features.chat.application.AnswerQuestionService;
import com.danycb.findocAnalyzer.features.chat.application.dto.AnswerResult;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalCandidate;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalOutcome;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import com.danycb.findocAnalyzer.features.vault.adapter.out.edgar.FixtureFilingSectionsAdapter;
import com.danycb.findocAnalyzer.features.vault.adapter.out.vector.DocumentVectorPersistence;
import com.danycb.findocAnalyzer.features.vault.adapter.out.vector.PgVectorIndexAdapter;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.danycb.findocAnalyzer.infra.config.VectorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("eval")
@Testcontainers(disabledWithoutDocker = true)
class RetrievalEvaluationIT {
    private static final String DATASET = "tesla-2025-v1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static DataSource dataSource;
    private static PgVectorIndexAdapter index;
    private static PgVectorSearchAdapter search;
    private static FixtureFilingSectionsAdapter fixtures;
    private static UUID evalTeam;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        Flyway.configure().dataSource(source).load().migrate();
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE " + POSTGRES.getDatabaseName() + " SET hnsw.ef_search = 100");
        }

        VectorConfig vectorConfig = new VectorConfig();
        EmbeddingModel model = vectorConfig.embeddingModel();
        EmbeddingStore<TextSegment> store = vectorConfig.embeddingStore(source);
        index = new PgVectorIndexAdapter(model, store, (DocumentVectorPersistence) store);
        RetrievalProperties properties = new RetrievalProperties();
        properties.setTracePoolSize(50);
        properties.setMaxSections(6);
        properties.setMinScore(0.60);
        search = new PgVectorSearchAdapter(model, store, properties);
        fixtures = new FixtureFilingSectionsAdapter(JSON,
                Path.of(System.getProperty("findoc.eval.root", "../evals"), "corpus", "tesla-2025"));

        evalTeam = UUID.randomUUID();
        ingestFixture(evalTeam, "0001628280-25-003063");
        ingestFixture(evalTeam, "0001104659-25-042659");
        UUID foreignTeam = UUID.randomUUID();
        Document foreign = Document.builder()
                .id(UUID.randomUUID()).teamId(foreignTeam).fileName("foreign")
                .uploadedAt(Instant.now()).source(DocumentSource.EDGAR).ticker("TSLA")
                .formType("10-K/A").baseFormType("10-K").amendment(true)
                .accessionNumber("foreign-accession").amendsAccessionNumber("foreign-original")
                .filingDate(LocalDate.of(2025, 4, 30)).build();
        insertParent(foreign);
        index.ingest(List.of(new ParsedSection(null, "Explanatory Note", "Explanatory Note",
                "What does Tesla's 10-K/A amend? What does Tesla's 10-K/A amend?")), foreign);
    }

    @Test
    void scoresRealMiniLmRetrievalAndWritesAnAttributableScorecard() throws Exception {
        List<JsonNode> questions = EvaluationInputs.readJsonLines(JSON, "datasets/" + DATASET + ".jsonl");
        List<Map<String, Object>> cases = new ArrayList<>();
        int answerableCases = 0;
        int evidenceHits = 0;
        int sectionHits = 0;
        int accessionHits = 0;
        int versionCases = 0;
        int versionHits = 0;
        int unanswerableCases = 0;
        int unanswerableWithRetrieval = 0;
        double reciprocalRankTotal = 0;
        double accessionPrecisionTotal = 0;

        for (JsonNode question : questions) {
            if (!"verified".equals(question.path("verificationStatus").asText())) {
                continue;
            }
            RetrievalOutcome outcome = search.search(question.path("question").asText(), evalTeam);
            assertThat(outcome.candidates()).allSatisfy(candidate -> {
                assertThat(candidate.effective()).isTrue();
                assertThat(candidate.accessionNumber()).isNotEqualTo("foreign-accession");
            });

            Set<String> expectedAccessions = values(question.path("expectedAccessions"));
            Set<String> expectedSections = values(question.path("expectedSections"));
            boolean answerable = question.path("answerable").asBoolean();
            boolean accessionHit = anySelected(outcome.selected(), expectedAccessions, Set.of());
            boolean sectionHit = anySelected(outcome.selected(), Set.of(), expectedSections);
            boolean evidenceHit = evidenceHit(outcome.selected(), question.path("goldEvidence"));
            double reciprocalRank = reciprocalRank(outcome.candidates(), expectedAccessions, expectedSections);
            double accessionPrecision = outcome.selected().isEmpty() || expectedAccessions.isEmpty() ? 0.0
                    : outcome.selected().stream().filter(chunk -> expectedAccessions.contains(chunk.accessionNumber())).count()
                    / (double) outcome.selected().size();

            if (answerable) {
                answerableCases++;
                if (evidenceHit) evidenceHits++;
                if (sectionHit) sectionHits++;
                if (accessionHit) accessionHits++;
                reciprocalRankTotal += reciprocalRank;
                accessionPrecisionTotal += accessionPrecision;
            } else {
                unanswerableCases++;
                if (!outcome.selected().isEmpty()) unanswerableWithRetrieval++;
            }
            if (Set.of("amendment-purpose", "version-selection").contains(question.path("category").asText())) {
                versionCases++;
                if (!outcome.selected().isEmpty()
                        && expectedAccessions.contains(outcome.selected().getFirst().accessionNumber())) {
                    versionHits++;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", question.path("id").asText());
            result.put("evidenceHit", evidenceHit);
            result.put("sectionHit", sectionHit);
            result.put("accessionHit", accessionHit);
            result.put("reciprocalRank", reciprocalRank);
            result.put("accessionPrecision", accessionPrecision);
            result.put("selected", selectedRows(outcome.selected()));
            result.put("candidates", outcome.candidates());
            cases.add(result);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("evidenceRecallAt6", ratio(evidenceHits, answerableCases));
        metrics.put("sectionRecallAt6", ratio(sectionHits, answerableCases));
        metrics.put("mrr", ratio(reciprocalRankTotal, answerableCases));
        metrics.put("accessionPrecision", ratio(accessionPrecisionTotal, answerableCases));
        metrics.put("accessionAccuracy", ratio(accessionHits, answerableCases));
        metrics.put("amendmentOriginalSelectionAccuracy", ratio(versionHits, versionCases));
        metrics.put("effectiveVersionAccuracy", ratio(versionHits, versionCases));
        metrics.put("unanswerableRetrievalRate", ratio(unanswerableWithRetrieval, unanswerableCases));
        metrics.put("tenantLeakage", 0);

        Path report = Path.of("target", "eval-reports", "retrieval-" + DATASET + ".json");
        Files.createDirectories(report.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), Map.of(
                "dataset", DATASET,
                "embeddingModel", "AllMiniLmL6V2QuantizedEmbeddingModel",
                "tracePoolSize", 50,
                "maxSections", 6,
                "minScore", 0.60,
                "metrics", metrics,
                "cases", cases));

        Map<String, Object> amendmentPurpose = cases.stream()
                .filter(row -> row.get("id").equals("tsla-2025-amendment-purpose"))
                .findFirst().orElseThrow();
        assertThat(amendmentPurpose.get("evidenceHit")).isEqualTo(true);
        assertThat(amendmentPurpose.get("accessionHit")).isEqualTo(true);
        assertThat(metrics.get("tenantLeakage")).isEqualTo(0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    void productionServicePathAnswersTheCriticalAmendmentQuestionWithGemini() {
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("GEMINI_API_KEY"))
                .modelName("gemini-2.5-flash-lite")
                .temperature(0.0)
                .seed(42)
                .timeout(Duration.ofSeconds(30))
                .maxOutputTokens(4096)
                .maxRetries(2)
                .logRequestsAndResponses(false)
                .build();
        LangChain4jAiService aiService = AiServices.create(LangChain4jAiService.class, model);
        AnswerQuestionService service = new AnswerQuestionService(new LangChain4jLlmAdapter(aiService), search);

        AnswerResult result = service.execute("What does Tesla's 10-K/A amend?", evalTeam);

        assertThat(result.answer()).contains("Part III", "10", "11", "12", "13", "14");
        assertThat(result.citations()).isNotEmpty().allSatisfy(citation ->
                assertThat(citation.accessionNumber()).isEqualTo("0001104659-25-042659"));
        assertThat(result.answer()).doesNotContain("S1", "embeddingId");
    }

    private static void ingestFixture(UUID teamId, String accession) throws Exception {
        FilingSectionsResult filing = fixtures.fetchSections("TSLA", accession);
        Document document = Document.builder()
                .id(UUID.randomUUID()).teamId(teamId).fileName("TSLA " + filing.formType())
                .uploadedAt(Instant.now()).source(DocumentSource.EDGAR).ticker("TSLA")
                .formType(filing.formType()).baseFormType(filing.formType().replace("/A", ""))
                .amendment(filing.formType().endsWith("/A")).accessionNumber(filing.accessionNumber())
                .amendsAccessionNumber(filing.amendsAccessionNumber()).filingDate(filing.filingDate())
                .reportDate(filing.reportDate()).build();
        insertParent(document);
        index.ingest(filing.sections(), document);
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

    private static Set<String> values(JsonNode array) {
        Set<String> values = new java.util.HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static boolean anySelected(List<RetrievedChunk> selected, Set<String> accessions, Set<String> sections) {
        return selected.stream().anyMatch(chunk ->
                (accessions.isEmpty() || accessions.contains(chunk.accessionNumber()))
                        && (sections.isEmpty() || sections.contains(chunk.sectionItem())));
    }

    private static boolean evidenceHit(List<RetrievedChunk> selected, JsonNode evidence) {
        for (JsonNode span : evidence) {
            int start = span.path("charStart").asInt();
            int end = span.path("charEnd").asInt();
            for (RetrievedChunk chunk : selected) {
                if (span.path("accession").asText().equals(chunk.accessionNumber())
                        && span.path("sectionItem").asText().equals(chunk.sectionItem())
                        && chunk.chunkStart() != null
                        && overlap(start, end, chunk.chunkStart(), chunk.chunkStart() + chunk.text().length()) >= 50) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int overlap(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return Math.max(0, Math.min(firstEnd, secondEnd) - Math.max(firstStart, secondStart));
    }

    private static double reciprocalRank(List<RetrievalCandidate> candidates,
                                         Set<String> accessions, Set<String> sections) {
        return candidates.stream()
                .filter(candidate -> accessions.contains(candidate.accessionNumber())
                        && sections.contains(candidate.sectionItem()))
                .mapToInt(RetrievalCandidate::rank)
                .min().stream().mapToDouble(rank -> 1.0 / rank).findFirst().orElse(0.0);
    }

    private static double ratio(double numerator, int denominator) {
        return denominator == 0 ? 0.0 : numerator / denominator;
    }

    private static List<Map<String, Object>> selectedRows(List<RetrievedChunk> chunks) {
        return chunks.stream().map(chunk -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("embeddingId", chunk.embeddingId());
            row.put("accessionNumber", chunk.accessionNumber());
            row.put("formType", chunk.formType());
            row.put("filingDate", chunk.filingDate() == null ? null : chunk.filingDate().toString());
            row.put("sectionItem", chunk.sectionItem());
            row.put("page", chunk.page());
            row.put("chunkStart", chunk.chunkStart());
            row.put("score", chunk.score());
            row.put("text", chunk.text());
            return row;
        }).toList();
    }
}
