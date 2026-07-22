package com.danycb.findocAnalyzer.features.vault.application.dto;

import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;

import java.util.UUID;

public record ImportFilingResult(UUID documentId, String fileName, DocumentStatus status) {
}
