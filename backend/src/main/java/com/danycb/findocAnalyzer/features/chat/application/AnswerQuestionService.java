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

    private static final String NO_ANSWER_MARKER =
            "The current document vault does not contain information to answer this question.";

    @Override
    public String execute(String question, UUID teamId) {
        String answer = searchAndAnswer(question, question, teamId);

        if (answer.contains(NO_ANSWER_MARKER)) {
            String searchQuery = llm.rewriteForSearch(question);
            answer = searchAndAnswer(searchQuery, question, teamId);
        }

        if (answer.contains(NO_ANSWER_MARKER)) {
            String hypotheticalAnswer = llm.generateHypotheticalAnswer(question);
            answer = searchAndAnswer(hypotheticalAnswer, question, teamId);
        }

        return answer;
    }

    private String searchAndAnswer(String searchQuery, String question, UUID teamId) {
        List<RetrievedChunk> chunks = vectorSearch.search(searchQuery, teamId);

        if (chunks.isEmpty()) {
            return NO_ANSWER_MARKER;
        }

        String context = chunks.stream()
                .map(chunk -> String.format("[%s, Pg %s] %s",
                        sourceLabel(chunk),
                        chunk.page(),
                        chunk.text()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return llm.answerWithContext(context, question);
    }

    private String sourceLabel(RetrievedChunk chunk) {
        if (chunk.title() == null || chunk.title().isBlank()) {
            return chunk.fileName();
        }
        return chunk.fileName() + " - " + chunk.title();
    }
}
