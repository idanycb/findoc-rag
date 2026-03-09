package com.danycb.findocAnalyzer.dto;

import com.danycb.findocAnalyzer.model.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DocumentResponseDTO {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Instant uploadedAt;
    private DocumentStatus status;
}
