package com.danycb.findocAnalyzer.features.chat.application;

import com.danycb.findocAnalyzer.features.chat.application.dto.AnswerResult;
import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.Citation;
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
    public AnswerResult execute(String question, UUID teamId) {
        AnswerResult result = searchAndAnswer(question, question, teamId);

        if (result.answer().contains(NO_ANSWER_MARKER)) {
            String searchQuery = llm.rewriteForSearch(question);
            result = searchAndAnswer(searchQuery, question, teamId);
        }

        if (result.answer().contains(NO_ANSWER_MARKER)) {
            String hypotheticalAnswer = llm.generateHypotheticalAnswer(question);
            result = searchAndAnswer(hypotheticalAnswer, question, teamId);
        }

        return result;
    }

    private AnswerResult searchAndAnswer(String searchQuery, String question, UUID teamId) {
        List<RetrievedChunk> chunks = vectorSearch.search(searchQuery, teamId);

        if (chunks.isEmpty()) {
            return new AnswerResult(NO_ANSWER_MARKER, List.of());
        }

        String context = chunks.stream()
                .map(chunk -> String.format("[%s, Pg %s%s] %s",
                        sourceLabel(chunk),
                        chunk.page(),
                        accessionLabel(chunk),
                        chunk.text()))
                .collect(Collectors.joining("\n\n---\n\n"));

        String answer = llm.answerWithContext(context, question);
        List<Citation> citations = chunks.stream()
                .map(this::citation)
                .toList();
        return new AnswerResult(answer, citations);
    }

    private String sourceLabel(RetrievedChunk chunk) {
        String section = sectionLabel(chunk);
        if (section == null) {
            return chunk.fileName();
        }
        return chunk.fileName() + " - " + section;
    }

    private String sectionLabel(RetrievedChunk chunk) {
        boolean hasItem = chunk.sectionItem() != null && !chunk.sectionItem().isBlank();
        boolean hasTitle = chunk.title() != null && !chunk.title().isBlank();
        if (hasItem && hasTitle) {
            return chunk.sectionItem() + " " + chunk.title();
        }
        if (hasItem) {
            return chunk.sectionItem();
        }
        return hasTitle ? chunk.title() : null;
    }

    private String accessionLabel(RetrievedChunk chunk) {
        return chunk.accessionNumber() == null || chunk.accessionNumber().isBlank()
                ? ""
                : ", accession " + chunk.accessionNumber();
    }

    private Citation citation(RetrievedChunk chunk) {
        return new Citation(
                chunk.accessionNumber(),
                chunk.formType(),
                chunk.filingDate(),
                chunk.sectionItem(),
                chunk.title(),
                chunk.page(),
                chunk.text());
    }
}
