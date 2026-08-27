package com.danycb.findocAnalyzer.features.chat.domain;

public record RetrievalCandidate(
        String embeddingId,
        double score,
        int rank,
        String accessionNumber,
        String formType,
        String sectionItem,
        boolean effective,
        boolean selected,
        DiscardReason discardReason
) {
    public enum DiscardReason {
        BELOW_THRESHOLD,
        DUPLICATE_TEXT,
        DUPLICATE_SECTION,
        OVER_SECTION_CAP
    }
}
