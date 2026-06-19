package com.danycb.findocAnalyzer.features.chat.adapter.out.vector;

import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PgVectorSearchAdapter implements VectorSearchPort {
    private static final int MAX_RESULTS = 5;
    private static final double MIN_SCORE = 0.60;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public List<RetrievedChunk> search(String query) {
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(query).content())
                        .maxResults(MAX_RESULTS)
                        .minScore(MIN_SCORE)
                        .build()
        );

        return result.matches().stream()
                .map(this::toRetrievedChunk)
                .toList();
    }

    private RetrievedChunk toRetrievedChunk(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        return new RetrievedChunk(
                match.embeddingId(),
                segment.metadata().getString("file_name"),
                segment.metadata().getInteger("page"),
                segment.text()
        );
    }
}
