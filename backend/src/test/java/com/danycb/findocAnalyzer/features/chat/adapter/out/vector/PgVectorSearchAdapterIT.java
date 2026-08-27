package com.danycb.findocAnalyzer.features.chat.adapter.out.vector;

import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import com.danycb.findocAnalyzer.infra.config.VectorConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for {@link PgVectorSearchAdapter}: retrieval against the real
 * langchain4j pgvector store (built from the production {@link VectorConfig}) on a pgvector Postgres.
 * A deterministic keyword-based fake {@link EmbeddingModel} maps each phrase to an orthogonal unit
 * vector, so cosine similarity is controllable: a matching keyword scores 1.0, a non-matching one
 * scores below the adapter's minimum. Verifies tenant isolation, the score threshold, text-based
 * de-duplication, and the maximum-sections cap.
 */
@Testcontainers(disabledWithoutDocker = true)
class PgVectorSearchAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie").asCompatibleSubstituteFor("postgres"));

    private static DataSource dataSource;
    private static EmbeddingStore<TextSegment> store;
    private static final KeywordEmbeddingModel MODEL = new KeywordEmbeddingModel();

    private final PgVectorSearchAdapter adapter = new PgVectorSearchAdapter(
            MODEL, store, new RetrievalProperties());

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
    void clean() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM document_embeddings");
            s.execute("DELETE FROM document_metadata");
        }
    }

    @Test
    void searchReturnsOnlyCallingTeamsChunks() throws Exception {
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        ingest(teamA, "ALPHA revenue for team A");
        ingest(teamB, "ALPHA revenue for team B");

        List<RetrievedChunk> results = adapter.search("ALPHA", teamA).selected();

        assertThat(results).extracting(RetrievedChunk::text)
                .containsExactly("ALPHA revenue for team A");
    }

    @Test
    void searchExcludesChunksBelowMinScore() throws Exception {
        UUID team = UUID.randomUUID();
        ingest(team, "ALPHA relevant chunk");
        ingest(team, "BETA unrelated chunk");

        List<RetrievedChunk> results = adapter.search("ALPHA", team).selected();

        assertThat(results).extracting(RetrievedChunk::text).containsExactly("ALPHA relevant chunk");
    }

    @Test
    void searchDeduplicatesChunksWithIdenticalText() throws Exception {
        UUID team = UUID.randomUUID();
        ingest(team, "ALPHA duplicated text");
        ingest(team, "ALPHA duplicated text");

        List<RetrievedChunk> results = adapter.search("ALPHA", team).selected();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().text()).isEqualTo("ALPHA duplicated text");
    }

    @Test
    void searchCapsResultsAtMaxSections() throws Exception {
        UUID team = UUID.randomUUID();
        for (int i = 0; i < 8; i++) {
            ingest(team, "ALPHA chunk number " + i);
        }

        List<RetrievedChunk> results = adapter.search("ALPHA", team).selected();

        assertThat(results).hasSize(6);
    }

    @Test
    void searchReturnsOnlyEffectiveVersionAndCarriesActualSourceAccession() throws Exception {
        UUID team = UUID.randomUUID();
        ingest(team, "ALPHA original risks", false, "original-accession", "10-K", "2024-11-01");
        ingest(team, "ALPHA amended risks", true, "amendment-accession", "10-K/A", "2025-01-02");

        List<RetrievedChunk> results = adapter.search("ALPHA", team).selected();

        assertThat(results).singleElement().satisfies(chunk -> {
            assertThat(chunk.text()).isEqualTo("ALPHA amended risks");
            assertThat(chunk.accessionNumber()).isEqualTo("amendment-accession");
            assertThat(chunk.formType()).isEqualTo("10-K/A");
            assertThat(chunk.filingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
            assertThat(chunk.sectionItem()).isEqualTo("Item 1A");
        });
    }

    // ---- ingestion helper -------------------------------------------------------------------

    private void ingest(UUID teamId, String text) throws Exception {
        ingest(teamId, text, true, UUID.randomUUID().toString(), "10-K", "2024-11-01");
    }

    private void ingest(UUID teamId, String text, boolean effective, String accession,
                        String formType, String filingDate) throws Exception {
        UUID docId = insertParentDocument();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("document_id", docId.toString());
        metadata.put("team_id", teamId.toString());
        metadata.put("file_name", "doc.pdf");
        metadata.put("page", 1);
        metadata.put("chunk_index", 0);
        metadata.put("section_title", "Section");
        metadata.put("section_text", text);
        metadata.put("section_item", "Item 1A");
        metadata.put("accession_number", accession);
        metadata.put("original_accession_number", "original-accession");
        metadata.put("form_type", formType);
        metadata.put("filing_date", filingDate);
        metadata.put("effective", Boolean.toString(effective));
        TextSegment segment = TextSegment.from(text, new dev.langchain4j.data.document.Metadata(metadata));
        store.add(MODEL.embedAll(List.of(segment)).content().getFirst(), segment);
    }

    private UUID insertParentDocument() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO document_metadata (id, file_name, uploaded_at, status, source) "
                             + "VALUES (?, 'doc.pdf', now(), 'COMPLETED', 'UPLOAD')")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
        return id;
    }

    // ---- fake -------------------------------------------------------------------------------

    /**
     * Maps a phrase to an orthogonal unit vector keyed on its leading keyword, so that identical
     * keywords are collinear (cosine 1.0) and different keywords are orthogonal (cosine 0.0).
     */
    static final class KeywordEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = segments.stream()
                    .map(s -> Embedding.from(vectorFor(s.text())))
                    .toList();
            return Response.from(embeddings);
        }

        private static float[] vectorFor(String text) {
            float[] vector = new float[384];
            vector[dimensionFor(text)] = 1.0f;
            return vector;
        }

        private static int dimensionFor(String text) {
            String upper = text.toUpperCase();
            if (upper.contains("ALPHA")) {
                return 1;
            }
            if (upper.contains("BETA")) {
                return 2;
            }
            return 0;
        }
    }
}
