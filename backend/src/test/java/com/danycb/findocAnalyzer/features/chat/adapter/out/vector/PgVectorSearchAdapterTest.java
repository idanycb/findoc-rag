package com.danycb.findocAnalyzer.features.chat.adapter.out.vector;

import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalCandidate;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PgVectorSearchAdapter#search}: request construction, mapping of embedding
 * matches to {@link RetrievedChunk}, text-based de-duplication and the section cap — all with
 * recording fakes for the langchain4j collaborators, so no real pgvector database is required.
 */
class PgVectorSearchAdapterTest {

    private final StubEmbeddingModel embeddingModel = new StubEmbeddingModel();
    private final StubEmbeddingStore embeddingStore = new StubEmbeddingStore();
    private final PgVectorSearchAdapter adapter = new PgVectorSearchAdapter(
            embeddingModel, embeddingStore, new RetrievalProperties());

    private final UUID teamId = UUID.randomUUID();

    @Test
    void mapsMatchMetadataToRetrievedChunk() {
        embeddingStore.result = matches(match("id-1", "Item 1. Business", 3, "The full section text.", "chunk text"));

        List<RetrievedChunk> chunks = adapter.search("what is the business?", teamId).selected();

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.embeddingId()).isEqualTo("id-1");
            assertThat(chunk.fileName()).isEqualTo("10k.pdf");
            assertThat(chunk.title()).isEqualTo("Item 1. Business");
            assertThat(chunk.page()).isEqualTo(3);
            assertThat(chunk.text()).isEqualTo("chunk text");
            assertThat(chunk.accessionNumber()).isEqualTo("0000320193-25-000020");
            assertThat(chunk.formType()).isEqualTo("10-K/A");
            assertThat(chunk.filingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
            assertThat(chunk.sectionItem()).isEqualTo("Item 1");
            assertThat(chunk.score()).isEqualTo(0.9);
        });
    }

    @Test
    void traceKeepsDiscardedCandidatesAndReasons() {
        embeddingStore.result = matches(
                match("id-1", "A", 1, "body", "same body"),
                matchWithScore("id-2", "B", 2, "same body", 0.8),
                matchWithScore("id-3", "C", 3, "below", 0.5));

        var outcome = adapter.search("q", teamId);

        assertThat(outcome.selected()).hasSize(1);
        assertThat(outcome.candidates()).extracting(RetrievalCandidate::discardReason)
                .containsExactly(null, RetrievalCandidate.DiscardReason.DUPLICATE_TEXT,
                        RetrievalCandidate.DiscardReason.BELOW_THRESHOLD);
    }

    @Test
    void fallsBackToSegmentTextWhenSectionTextMetadataMissing() {
        embeddingStore.result = matches(match("id-1", "Risk", 4, null, "segment body only"));

        List<RetrievedChunk> chunks = adapter.search("risks", teamId).selected();

        assertThat(chunks).singleElement().satisfies(chunk ->
                assertThat(chunk.text()).isEqualTo("segment body only"));
    }

    @Test
    void expandsAChildChunkWithBoundedParentContext() {
        String parent = "P".repeat(1_200) + "answering evidence" + "S".repeat(500);
        embeddingStore.result = matches(matchWithParent("id-1", "Note", "Explanatory Note",
                1, parent, 1_200, "answering evidence", 0.9));

        RetrievedChunk chunk = adapter.search("q", teamId).selected().getFirst();

        assertThat(chunk.chunkStart()).isEqualTo(300);
        assertThat(chunk.text()).isEqualTo(parent.substring(300, 1_518));
    }

    @Test
    void deduplicatesChunksWithIdenticalText() {
        embeddingStore.result = matches(
                match("id-1", "A", 1, "full A", "same body"),
                match("id-2", "B", 2, "full B", "same body"));

        List<RetrievedChunk> chunks = adapter.search("q", teamId).selected();

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.embeddingId()).isEqualTo("id-1"); // first wins
            assertThat(chunk.text()).isEqualTo("same body");
        });
    }

    @Test
    void limitsResultsToMaxSections() {
        List<EmbeddingMatch<TextSegment>> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(matchWithSection("id-" + i, "Title " + i, "Item " + i, i + 1, "seg " + i, 0.9));
        }
        embeddingStore.result = new EmbeddingSearchResult<>(many);

        List<RetrievedChunk> chunks = adapter.search("q", teamId).selected();

        assertThat(chunks).hasSize(6); // MAX_SECTIONS
    }

    @Test
    void selectsAtMostOneChunkPerSection() {
        embeddingStore.result = matches(
                matchWithSection("id-1", "Risks", "Item 1A", 1, "first risk chunk", 0.9),
                matchWithSection("id-2", "Risks", "Item 1A", 1, "second risk chunk", 0.8),
                matchWithSection("id-3", "Business", "Item 1", 1, "business chunk", 0.7));

        var outcome = adapter.search("q", teamId);

        assertThat(outcome.selected()).extracting(RetrievedChunk::sectionItem)
                .containsExactly("Item 1A", "Item 1");
        assertThat(outcome.candidates()).extracting(RetrievalCandidate::discardReason)
                .containsExactly(null, RetrievalCandidate.DiscardReason.DUPLICATE_SECTION, null);
    }

    @Test
    void buildsRequestScopedToTeamWithScoreAndResultCaps() {
        embeddingStore.result = matches(match("id-1", "T", 1, "b", "s"));

        adapter.search("q", teamId);

        EmbeddingSearchRequest request = embeddingStore.lastRequest;
        assertThat(request).isNotNull();
        assertThat(request.filter()).as("results must be scoped to the caller's team").isNotNull();
        assertThat(request.maxResults()).isEqualTo(15);
        assertThat(request.minScore()).isZero();
        assertThat(request.queryEmbedding()).isNotNull();
    }

    @Test
    void emptyStoreResultYieldsNoChunks() {
        embeddingStore.result = new EmbeddingSearchResult<>(List.of());

        assertThat(adapter.search("q", teamId).selected()).isEmpty();
    }

    // ---- helpers & fakes --------------------------------------------------------------------

    private EmbeddingSearchResult<TextSegment> matches(EmbeddingMatch<TextSegment> first,
                                                       EmbeddingMatch<TextSegment>... rest) {
        List<EmbeddingMatch<TextSegment>> all = new ArrayList<>();
        all.add(first);
        all.addAll(List.of(rest));
        return new EmbeddingSearchResult<>(all);
    }

    private EmbeddingMatch<TextSegment> match(String id, String title, int page,
                                              String sectionText, String segmentText) {
        return matchWithParent(id, title, "Item 1", page, sectionText, null, segmentText, 0.9);
    }

    private EmbeddingMatch<TextSegment> matchWithScore(String id, String title, int page,
                                                       String segmentText, double score) {
        return matchWithSection(id, title, "Item 1", page, segmentText, score);
    }

    private EmbeddingMatch<TextSegment> matchWithSection(String id, String title, String sectionItem, int page,
                                                         String segmentText, double score) {
        return matchWithParent(id, title, sectionItem, page, null, null, segmentText, score);
    }

    private EmbeddingMatch<TextSegment> matchWithParent(String id, String title, String sectionItem, int page,
                                                        String sectionText, Integer chunkStart,
                                                        String segmentText, double score) {
        Metadata metadata = new Metadata();
        metadata.put("file_name", "10k.pdf");
        metadata.put("section_title", title);
        metadata.put("section_item", sectionItem);
        metadata.put("page", page);
        metadata.put("accession_number", "0000320193-25-000020");
        metadata.put("form_type", "10-K/A");
        metadata.put("filing_date", "2025-01-02");
        metadata.put("effective", "true");
        if (sectionText != null) {
            metadata.put("section_text", sectionText);
        }
        if (chunkStart != null) {
            metadata.put("chunk_start", chunkStart);
        }
        TextSegment segment = new TextSegment(segmentText, metadata);
        return new EmbeddingMatch<>(score, id, new Embedding(new float[]{0.1f, 0.2f}), segment);
    }

    static class StubEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                embeddings.add(new Embedding(new float[]{0.1f, 0.2f}));
            }
            return Response.from(embeddings);
        }
    }

    static class StubEmbeddingStore implements EmbeddingStore<TextSegment> {
        EmbeddingSearchResult<TextSegment> result = new EmbeddingSearchResult<>(List.of());
        EmbeddingSearchRequest lastRequest;

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            this.lastRequest = request;
            return result;
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
        public void removeAll(Filter filter) {
        }
    }
}
