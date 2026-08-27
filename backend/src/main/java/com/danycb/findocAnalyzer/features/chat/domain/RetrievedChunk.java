package com.danycb.findocAnalyzer.features.chat.domain;

import java.time.LocalDate;

public record RetrievedChunk(
        String embeddingId,
        String fileName,
        String title,
        Integer page,
        String text,
        String accessionNumber,
        String formType,
        LocalDate filingDate,
        String sectionItem,
        Integer chunkStart,
        double score
) {
    public RetrievedChunk(
            String embeddingId,
            String fileName,
            String title,
            Integer page,
            String text,
            String accessionNumber,
            String formType,
            LocalDate filingDate,
            String sectionItem
    ) {
        this(embeddingId, fileName, title, page, text, accessionNumber, formType, filingDate,
                sectionItem, null, 0.0);
    }
}
