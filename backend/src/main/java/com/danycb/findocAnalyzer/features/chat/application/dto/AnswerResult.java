package com.danycb.findocAnalyzer.features.chat.application.dto;

import com.danycb.findocAnalyzer.features.chat.domain.Citation;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalAttempt;

import java.util.List;

public record AnswerResult(String answer, List<Citation> citations, List<RetrievalAttempt> attempts) {
    public AnswerResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }

    public AnswerResult(String answer, List<Citation> citations) {
        this(answer, citations, List.of());
    }
}
