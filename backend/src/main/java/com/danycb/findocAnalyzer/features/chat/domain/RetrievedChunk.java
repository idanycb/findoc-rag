package com.danycb.findocAnalyzer.features.chat.domain;

public record RetrievedChunk(
        String embeddingId,
        String fileName,
        int page,
        String text
) {
}
