package com.danycb.findocAnalyzer.features.vault.application.dto;

import java.util.UUID;

public record UploadResult(UUID documentId, String fileName, String status, String uploadUrl) {
}
