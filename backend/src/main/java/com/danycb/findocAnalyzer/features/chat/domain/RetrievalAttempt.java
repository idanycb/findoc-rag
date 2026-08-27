package com.danycb.findocAnalyzer.features.chat.domain;

public record RetrievalAttempt(
        AttemptType type,
        String searchQuery,
        RetrievalOutcome outcome
) {
}
