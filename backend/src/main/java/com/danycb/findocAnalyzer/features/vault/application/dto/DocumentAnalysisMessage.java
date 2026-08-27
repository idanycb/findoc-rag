package com.danycb.findocAnalyzer.features.vault.application.dto;

import java.util.UUID;

public record DocumentAnalysisMessage(UUID requestId, UUID documentId, String objectKey) {
    public DocumentAnalysisMessage(UUID documentId, String objectKey) {
        this(null, documentId, objectKey);
    }
}
