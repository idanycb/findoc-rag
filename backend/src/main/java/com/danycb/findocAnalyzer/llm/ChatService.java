package com.danycb.findocAnalyzer.llm;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final AiEngine aiEngine;

    public String answerQuestion(String question, UUID tenantId) {
        List<String> searchVariations = aiEngine.generateSearchVariations(question);
        searchVariations.add(question);

        List<EmbeddingMatch<TextSegment>> initialCandidates = Flux.fromIterable(searchVariations)
                .parallel()
                .runOn(Schedulers.boundedElastic())
                .flatMap(q -> performVectorSearch(q, tenantId))
                .sequential()
                .collectList()
                .map(this::deduplicateMatches)
                .block();

        if (initialCandidates == null || initialCandidates.isEmpty()) {
            return "No relevant financial data found in your tenant vault.";
        }

        String chunkList = IntStream.range(0, initialCandidates.size())
                .mapToObj(i -> String.format("ID %d: %s", i, initialCandidates.get(i).embedded().text()))
                .collect(Collectors.joining("\n\n"));

        String result = aiEngine.selectRelevantIndices(question, chunkList);

        List<TextSegment> refinedContext = parseIndices(result, initialCandidates);

        if (refinedContext == null || refinedContext.isEmpty()) {
            return "I found some documents, but they don't contain a specific answer to your question.";
        }

        String context = refinedContext.stream()
                .map(s -> String.format("[File: %s, Pg: %s] %s",
                        s.metadata().getString("file_name"),
                        s.metadata().getInteger("page"),
                        s.text()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return aiEngine.answerWithContext(context, question);
    }

    private List<TextSegment> parseIndices(String result, List<EmbeddingMatch<TextSegment>> matches) {
        if (result == null || result.equalsIgnoreCase("NONE")) return Collections.emptyList();
        try {
            return Arrays.stream(result.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .filter(i -> i >= 0 && i < matches.size())
                    .map(i -> matches.get(i).embedded())
                    .limit(5)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return matches.stream().limit(3).map(EmbeddingMatch::embedded).collect(Collectors.toList());
        }
    }

    private Flux<EmbeddingMatch<TextSegment>> performVectorSearch(String query, UUID tenantId) {
        Filter tenantFilter = metadataKey("tenant_id").isEqualTo(tenantId.toString());

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(query).content())
                        .filter(tenantFilter)
                        .maxResults(5)
                        .minScore(0.60)
                        .build()
        );

        return Flux.fromIterable(result.matches());
    }

    private List<EmbeddingMatch<TextSegment>> deduplicateMatches(List<EmbeddingMatch<TextSegment>> matches) {
        Map<String, EmbeddingMatch<TextSegment>> uniqueMatches = new ConcurrentHashMap<>();

        for (EmbeddingMatch<TextSegment> match : matches) {
            uniqueMatches.putIfAbsent(match.embeddingId(), match);
        }

        return new ArrayList<>(uniqueMatches.values());
    }
}
