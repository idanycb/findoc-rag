package com.danycb.findocAnalyzer.features.chat.application;

import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnswerQuestionService implements AnswerQuestionUseCase {
    private final LlmPort llm;
    private final VectorSearchPort vectorSearch;

    @Override
    public String execute(String question, UUID teamId) {
        List<RetrievedChunk> chunks = vectorSearch.search(question, teamId);

        if (chunks.isEmpty()) {
            return "No relevant financial data found in your document vault.";
        }

        String context = chunks.stream()
                .map(chunk -> String.format("[File: %s, Pg: %s] %s",
                        chunk.fileName(),
                        chunk.page(),
                        chunk.text()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return llm.answerWithContext(context, question);
    }
}
