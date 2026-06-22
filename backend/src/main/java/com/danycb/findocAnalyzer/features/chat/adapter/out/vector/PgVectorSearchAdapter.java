package com.danycb.findocAnalyzer.features.chat.adapter.out.vector;

import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Component
@RequiredArgsConstructor
public class PgVectorSearchAdapter implements VectorSearchPort {
    private static final int MAX_RESULTS = 15;
    private static final int MAX_SECTIONS = 6;
    private static final double MIN_SCORE = 0.60;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public List<RetrievedChunk> search(String query, UUID teamId) {
        Filter teamFilter = metadataKey("team_id").isEqualTo(teamId.toString());

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(query).content())
                        .maxResults(MAX_RESULTS)
                        .minScore(MIN_SCORE)
                        .filter(teamFilter)
                        .build()
        );

        return result.matches().stream()
                .map(this::toRetrievedChunk)
                .collect(Collectors.toMap(
                        RetrievedChunk::text,
                        chunk -> chunk,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new))
                .values().stream().limit(MAX_SECTIONS).toList();
    }

    private RetrievedChunk toRetrievedChunk(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        String sectionText = segment.metadata().getString("section_text");
        return new RetrievedChunk(
                match.embeddingId(),
                segment.metadata().getString("file_name"),
                segment.metadata().getInteger("page"),
                sectionText != null ? sectionText : segment.text()
        );
    }
}
