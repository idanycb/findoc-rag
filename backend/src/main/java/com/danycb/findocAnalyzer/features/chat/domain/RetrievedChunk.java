package com.danycb.findocAnalyzer.features.chat.domain;

public record RetrievedChunk(
        String embeddingId,
        String fileName,
        String title,
        int page,
        String text
) {
}
