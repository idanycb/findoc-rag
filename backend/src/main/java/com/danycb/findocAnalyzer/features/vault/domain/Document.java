package com.danycb.findocAnalyzer.features.vault.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class Document {
    private final UUID id;
    private final String fileName;
    private final Long fileSize;
    private final String contentType;
    private final Instant uploadedAt;
    private final Integer version;

    private DocumentStatus status;
    private String aiSummary;

    public boolean cannotAnalyze() {
        return status != DocumentStatus.FAILED && status != DocumentStatus.PENDING;
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = DocumentStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = DocumentStatus.FAILED;
    }

    public void markPendingForReanalysis() {
        this.status = DocumentStatus.PENDING;
    }
}
