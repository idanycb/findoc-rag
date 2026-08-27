package com.danycb.findocAnalyzer.features.chat.domain;

import java.util.List;

public record RetrievalOutcome(
        List<RetrievedChunk> selected,
        List<RetrievalCandidate> candidates
) {
    public RetrievalOutcome {
        selected = selected == null ? List.of() : List.copyOf(selected);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static RetrievalOutcome selectedOnly(List<RetrievedChunk> chunks) {
        return new RetrievalOutcome(chunks, List.of());
    }
}
