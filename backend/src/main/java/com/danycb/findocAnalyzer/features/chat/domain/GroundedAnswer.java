package com.danycb.findocAnalyzer.features.chat.domain;

import java.util.List;

public record GroundedAnswer(
        boolean answerable,
        String answer,
        List<ClaimCitation> citations
) {
    public GroundedAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
