package com.danycb.findocAnalyzer.features.chat.application;

import com.danycb.findocAnalyzer.features.chat.application.dto.AnswerResult;
import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.Citation;
import com.danycb.findocAnalyzer.features.chat.domain.ClaimCitation;
import com.danycb.findocAnalyzer.features.chat.domain.AttemptType;
import com.danycb.findocAnalyzer.features.chat.domain.GroundedAnswer;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalAttempt;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalOutcome;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AnswerQuestionService implements AnswerQuestionUseCase {
    private final LlmPort llm;
    private final VectorSearchPort vectorSearch;

    private static final String NO_ANSWER_MARKER =
            "The current document vault does not contain information to answer this question.";
    private static final Pattern SOURCE_MARKER = Pattern.compile("\\[(S\\d+)]");

    @Override
    public AnswerResult execute(String question, UUID teamId) {
        List<RetrievalAttempt> attempts = new ArrayList<>();
        AnswerAttempt attempt = searchAndAnswer(question, question, teamId, AttemptType.ORIGINAL, attempts);

        if (!attempt.answerable()) {
            String searchQuery = llm.rewriteForSearch(question);
            attempt = searchAndAnswer(searchQuery, question, teamId, AttemptType.REWRITE, attempts);
        }

        if (!attempt.answerable()) {
            String hypotheticalAnswer = llm.generateHypotheticalAnswer(question);
            attempt = searchAndAnswer(hypotheticalAnswer, question, teamId, AttemptType.HYDE, attempts);
        }

        return attempt.result();
    }

    private AnswerAttempt searchAndAnswer(String searchQuery, String question, UUID teamId,
                                          AttemptType type, List<RetrievalAttempt> attempts) {
        RetrievalOutcome outcome = vectorSearch.search(searchQuery, teamId);
        attempts.add(new RetrievalAttempt(type, searchQuery, outcome));
        List<RetrievedChunk> chunks = outcome.selected();

        if (chunks.isEmpty()) {
            return new AnswerAttempt(false, new AnswerResult(NO_ANSWER_MARKER, List.of(), attempts));
        }

        Map<String, RetrievedChunk> sources = sourceMap(chunks);
        String context = sources.entrySet().stream()
                .map(source -> String.format("[%s; %s%s%s] %s",
                        source.getKey(),
                        sourceLabel(source.getValue()),
                        pageLabel(source.getValue()),
                        accessionLabel(source.getValue()),
                        source.getValue().text()))
                .collect(Collectors.joining("\n\n---\n\n"));

        GroundedAnswer groundedAnswer = llm.answerWithContext(context, question);
        List<String> citedSourceIds = groundedAnswer.answerable()
                ? groundedAnswer.citations().stream()
                        .map(ClaimCitation::sourceId)
                        .filter(sources::containsKey)
                        .distinct()
                        .toList()
                : List.of();
        Map<String, Integer> citationNumbers = new LinkedHashMap<>();
        for (int index = 0; index < citedSourceIds.size(); index++) {
            citationNumbers.put(citedSourceIds.get(index), index + 1);
        }
        List<Citation> citations = citedSourceIds.stream()
                .map(sources::get)
                .map(this::citation)
                .toList();
        return new AnswerAttempt(
                groundedAnswer.answerable(),
                new AnswerResult(
                        groundedAnswer.answerable()
                                ? numberedAnswer(groundedAnswer.answer(), citationNumbers)
                                : NO_ANSWER_MARKER,
                        citations,
                        attempts));
    }

    private Map<String, RetrievedChunk> sourceMap(List<RetrievedChunk> chunks) {
        Map<String, RetrievedChunk> sources = new LinkedHashMap<>();
        for (int index = 0; index < chunks.size(); index++) {
            sources.put("S" + (index + 1), chunks.get(index));
        }
        return sources;
    }

    private String numberedAnswer(String answer, Map<String, Integer> citationNumbers) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        Matcher matcher = SOURCE_MARKER.matcher(answer);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            Integer number = citationNumbers.get(matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(number == null ? "" : "[" + number + "]"));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String pageLabel(RetrievedChunk chunk) {
        return chunk.page() == null ? "" : ", Pg " + chunk.page();
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
            if (chunk.sectionItem().equals(chunk.title())) {
                return chunk.sectionItem();
            }
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

    private record AnswerAttempt(boolean answerable, AnswerResult result) {
    }
}
