package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PgVectorIndexAdapter#ingest}: the chunking, metadata tagging and
 * empty-batch handling that runs before anything touches the embedding store. The langchain4j
 * {@link EmbeddingModel} and {@link EmbeddingStore} collaborators are replaced with recording
 * fakes so the logic is exercised without a real pgvector database.
 */
class PgVectorIndexAdapterTest {

    private final RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
    private final RecordingEmbeddingStore embeddingStore = new RecordingEmbeddingStore();
    private final PgVectorIndexAdapter adapter = new PgVectorIndexAdapter(embeddingModel, embeddingStore);

    private final UUID docId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();

    @Test
    void shortSection_producesSingleSegmentWithFullMetadata() {
        adapter.ingest(
                List.of(new ParsedSection(3, "Item 1. Business", "We design and sell products.")),
                docId, teamId, "10k.pdf");

        assertThat(embeddingStore.addedSegments).hasSize(1);
        TextSegment segment = embeddingStore.addedSegments.getFirst();
        assertThat(segment.text()).isEqualTo("We design and sell products.");
        assertThat(segment.metadata().getString("file_name")).isEqualTo("10k.pdf");
        assertThat(segment.metadata().getString("document_id")).isEqualTo(docId.toString());
        assertThat(segment.metadata().getString("team_id")).isEqualTo(teamId.toString());
        assertThat(segment.metadata().getInteger("page")).isEqualTo(3);
        assertThat(segment.metadata().getString("section_title")).isEqualTo("Item 1. Business");
        assertThat(segment.metadata().getString("section_text")).isEqualTo("We design and sell products.");
        assertThat(segment.metadata().getInteger("chunk_index")).isZero();
    }

    @Test
    void blankSections_areSkippedAndStoreIsNotTouched() {
        adapter.ingest(
                List.of(new ParsedSection(1, "Empty", "   "), new ParsedSection(2, "Null", null)),
                docId, teamId, "10k.pdf");

        assertThat(embeddingStore.addAllCalled).isFalse();
        assertThat(embeddingStore.addedSegments).isEmpty();
    }

    @Test
    void longSection_isSplitIntoMultipleChildChunksWithIncrementingIndex() {
        String longText = ("The quick brown fox jumps over the lazy dog. ").repeat(60); // ~2700 chars > 900

        adapter.ingest(
                List.of(new ParsedSection(5, "Long Section", longText)),
                docId, teamId, "10k.pdf");

        assertThat(embeddingStore.addedSegments).hasSizeGreaterThan(1);
        for (int i = 0; i < embeddingStore.addedSegments.size(); i++) {
            TextSegment segment = embeddingStore.addedSegments.get(i);
            assertThat(segment.metadata().getInteger("chunk_index")).isEqualTo(i);
            assertThat(segment.metadata().getString("section_title")).isEqualTo("Long Section");
            assertThat(segment.metadata().getInteger("page")).isEqualTo(5);
        }
    }

    @Test
    void blankTitle_omitsSectionTitleMetadata() {
        adapter.ingest(
                List.of(new ParsedSection(1, "   ", "Body without a title.")),
                docId, teamId, "10k.pdf");

        assertThat(embeddingStore.addedSegments).singleElement().satisfies(segment ->
                assertThat(segment.metadata().getString("section_title")).isNull());
    }

    @Test
    void ingestWithNoSections_doesNotCallStore() {
        adapter.ingest(List.of(), docId, teamId, "10k.pdf");

        assertThat(embeddingStore.addAllCalled).isFalse();
    }

    @Test
    void deleteByDocumentId_removesByFilter() {
        adapter.deleteByDocumentId(docId);

        assertThat(embeddingStore.removeFilter).isNotNull();
    }

    @Test
    void deleteByDocumentId_swallowsStoreFailures() {
        embeddingStore.throwOnRemove = true;

        // Deletion failures must not propagate — the caller (delete flow) should still succeed.
        adapter.deleteByDocumentId(docId);
    }

    // ---- fakes ------------------------------------------------------------------------------

    /** Returns one dummy embedding per input segment, preserving list size. */
    static class RecordingEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                embeddings.add(new Embedding(new float[]{0.1f, 0.2f, 0.3f}));
            }
            return Response.from(embeddings);
        }
    }

    static class RecordingEmbeddingStore implements EmbeddingStore<TextSegment> {
        final List<TextSegment> addedSegments = new ArrayList<>();
        boolean addAllCalled;
        Filter removeFilter;
        boolean throwOnRemove;

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
            addAllCalled = true;
            addedSegments.addAll(embedded);
            return List.of();
        }

        @Override
        public void removeAll(Filter filter) {
            if (throwOnRemove) {
                throw new RuntimeException("store unavailable");
            }
            removeFilter = filter;
        }

        // Unused abstract methods.
        @Override
        public String add(Embedding embedding) {
            return "";
        }

        @Override
        public void add(String id, Embedding embedding) {
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            return "";
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return List.of();
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            return new EmbeddingSearchResult<>(List.of());
        }
    }
}
