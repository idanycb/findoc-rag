package com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto;

import java.util.UUID;

public record ImportFilingResponse(UUID documentId, String fileName, String status) {
}
