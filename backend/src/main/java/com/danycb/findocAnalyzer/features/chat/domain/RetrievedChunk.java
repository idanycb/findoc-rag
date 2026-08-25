package com.danycb.findocAnalyzer.features.chat.domain;

import java.time.LocalDate;

public record RetrievedChunk(
        String embeddingId,
        String fileName,
        String title,
        int page,
        String text,
        String accessionNumber,
        String formType,
        LocalDate filingDate,
        String sectionItem
) {
}
