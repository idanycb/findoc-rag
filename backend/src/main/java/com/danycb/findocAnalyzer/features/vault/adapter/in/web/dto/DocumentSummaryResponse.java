package com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        String fileName,
        Long fileSize,
        String contentType,
        Instant uploadedAt,
        DocumentStatus status
) {
    public static DocumentSummaryResponse from(Document document) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getFileName(),
                document.getFileSize(),
                document.getContentType(),
                document.getUploadedAt(),
                document.getStatus()
        );
    }
}
