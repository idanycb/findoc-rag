package com.danycb.findocAnalyzer.features.chat.adapter.out.vector;

import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalCandidate;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalOutcome;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Component
public class PgVectorSearchAdapter implements VectorSearchPort {
    private static final int PARENT_CONTEXT_BEFORE = 900;
    private static final int PARENT_CONTEXT_AFTER = 300;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RetrievalProperties properties;

    public PgVectorSearchAdapter(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore,
                                 RetrievalProperties properties) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.properties = properties;
    }

    @Override
    public RetrievalOutcome search(String query, UUID teamId) {
        Filter teamFilter = metadataKey("team_id").isEqualTo(teamId.toString())
                .and(metadataKey("effective").isEqualTo("true"));

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(query).content())
                        .maxResults(properties.getTracePoolSize())
                        .minScore(0.0)
                        .filter(teamFilter)
                        .build()
        );

        List<RetrievedChunk> selected = new ArrayList<>();
        List<RetrievalCandidate> candidates = new ArrayList<>();
        Set<String> selectedTexts = new HashSet<>();
        Set<String> selectedSections = new HashSet<>();

        int rank = 0;
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            rank++;
            RetrievedChunk chunk = toRetrievedChunk(match);
            RetrievalCandidate.DiscardReason discardReason = null;
            if (match.score() < properties.getMinScore()) {
                discardReason = RetrievalCandidate.DiscardReason.BELOW_THRESHOLD;
            } else if (selectedTexts.contains(chunk.text())) {
                discardReason = RetrievalCandidate.DiscardReason.DUPLICATE_TEXT;
            } else if (selectedSections.contains(sectionKey(chunk))) {
                discardReason = RetrievalCandidate.DiscardReason.DUPLICATE_SECTION;
            } else if (selected.size() >= properties.getMaxSections()) {
                discardReason = RetrievalCandidate.DiscardReason.OVER_SECTION_CAP;
            } else {
                selectedTexts.add(chunk.text());
                selectedSections.add(sectionKey(chunk));
                selected.add(chunk);
            }
            candidates.add(toCandidate(match, rank, discardReason));
        }
        return new RetrievalOutcome(selected, candidates);
    }

    private String sectionKey(RetrievedChunk chunk) {
        if (chunk.sectionItem() != null && !chunk.sectionItem().isBlank()) {
            return String.valueOf(chunk.accessionNumber()) + '\u0000' + chunk.sectionItem();
        }
        return chunk.fileName() + '\u0000' + String.valueOf(chunk.title());
    }

    private RetrievedChunk toRetrievedChunk(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        ContextWindow context = contextWindow(segment);
        return new RetrievedChunk(
                match.embeddingId(),
                segment.metadata().getString("file_name"),
                segment.metadata().getString("section_title"),
                segment.metadata().getInteger("page"),
                context.text(),
                segment.metadata().getString("accession_number"),
                segment.metadata().getString("form_type"),
                parseDate(segment.metadata().getString("filing_date")),
                segment.metadata().getString("section_item"),
                context.start(),
                match.score()
        );
    }

    private ContextWindow contextWindow(TextSegment segment) {
        String parent = segment.metadata().getString("section_text");
        Integer childStart = segment.metadata().getInteger("chunk_start");
        if (parent == null || childStart == null || childStart < 0 || childStart > parent.length()) {
            return new ContextWindow(segment.text(), childStart);
        }
        int start = Math.max(0, childStart - PARENT_CONTEXT_BEFORE);
        int end = Math.min(parent.length(), childStart + segment.text().length() + PARENT_CONTEXT_AFTER);
        return new ContextWindow(parent.substring(start, end), start);
    }

    private RetrievalCandidate toCandidate(EmbeddingMatch<TextSegment> match, int rank,
                                           RetrievalCandidate.DiscardReason discardReason) {
        TextSegment segment = match.embedded();
        return new RetrievalCandidate(
                match.embeddingId(),
                match.score(),
                rank,
                segment.metadata().getString("accession_number"),
                segment.metadata().getString("form_type"),
                segment.metadata().getString("section_item"),
                Boolean.parseBoolean(segment.metadata().getString("effective")),
                discardReason == null,
                discardReason);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private record ContextWindow(String text, Integer start) {
    }
}
