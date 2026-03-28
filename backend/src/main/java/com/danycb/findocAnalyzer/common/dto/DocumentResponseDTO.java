package com.danycb.findocAnalyzer.common.dto;

import com.danycb.findocAnalyzer.document.DocumentStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class DocumentResponseDTO {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Instant uploadedAt;
    private String aiSummary;
    private DocumentStatus status;

    private String uploadUrl; // Direct-to-S3 Upload
}
