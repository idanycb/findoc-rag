package com.danycb.findocAnalyzer.features.vault.application.dto;

import java.util.UUID;

public record DocumentAnalysisMessage(UUID documentId, String objectKey) {
}
