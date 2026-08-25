package com.danycb.findocAnalyzer.features.chat.application.dto;

import com.danycb.findocAnalyzer.features.chat.domain.Citation;

import java.util.List;

public record AnswerResult(String answer, List<Citation> citations) {
    public AnswerResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
