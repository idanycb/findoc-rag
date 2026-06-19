package com.danycb.findocAnalyzer.features.chat.application;

import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AnswerQuestionService implements AnswerQuestionUseCase {
    private final LlmPort llm;
    private final VectorSearchPort vectorSearch;

    @Override
    public String execute(String question) {
        List<String> searchQueries = new ArrayList<>(llm.generateSearchVariations(question));
        searchQueries.add(question);

        List<RetrievedChunk> initialCandidates = deduplicateChunks(
                searchQueries.parallelStream()
                        .flatMap(query -> vectorSearch.search(query).stream())
                        .toList()
        );

        if (initialCandidates.isEmpty()) {
            return "No relevant financial data found in your document vault.";
        }

        String chunkList = IntStream.range(0, initialCandidates.size())
                .mapToObj(i -> String.format("ID %d: %s", i, initialCandidates.get(i).text()))
                .collect(Collectors.joining("\n\n"));

        String selectedIndices = llm.selectRelevantIndices(question, chunkList);
        List<RetrievedChunk> refinedContext = parseIndices(selectedIndices, initialCandidates);

        if (refinedContext.isEmpty()) {
            return "I found some documents, but they don't contain a specific answer to your question.";
        }

        String context = refinedContext.stream()
                .map(chunk -> String.format("[File: %s, Pg: %s] %s",
                        chunk.fileName(),
                        chunk.page(),
                        chunk.text()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return llm.answerWithContext(context, question);
    }

    private List<RetrievedChunk> parseIndices(String result, List<RetrievedChunk> chunks) {
        if (result == null || result.equalsIgnoreCase("NONE")) {
            return Collections.emptyList();
        }

        try {
            return Arrays.stream(result.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .filter(i -> i >= 0 && i < chunks.size())
                    .map(chunks::get)
                    .limit(5)
                    .toList();
        } catch (Exception e) {
            return chunks.stream().limit(3).toList();
        }
    }

    private List<RetrievedChunk> deduplicateChunks(List<RetrievedChunk> chunks) {
        LinkedHashMap<String, RetrievedChunk> uniqueChunks = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            uniqueChunks.putIfAbsent(chunk.embeddingId(), chunk);
        }
        return new ArrayList<>(uniqueChunks.values());
    }
}
