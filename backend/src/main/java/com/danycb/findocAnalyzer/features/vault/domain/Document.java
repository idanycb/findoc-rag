package com.danycb.findocAnalyzer.features.vault.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class Document {
    private final UUID id;
    private final UUID teamId;
    private final String fileName;
    private final Long fileSize;
    private final String contentType;
    private final Instant uploadedAt;
    private final Integer version;
    @Builder.Default
    private final DocumentSource source = DocumentSource.UPLOAD;
    private final String cik;
    private final String ticker;
    private final String companyName;
    private final String formType;
    private final String fiscalPeriod;
    private final LocalDate reportDate;
    private final LocalDate filingDate;
    private final String accessionNumber;
    private final String sourceUrl;

    private DocumentStatus status;
    private Instant lastAnalyzedAt;

    public DocumentSource getSource() {
        return source != null ? source : DocumentSource.UPLOAD;
    }

    public boolean cannotAnalyze() {
        return status != DocumentStatus.FAILED && status != DocumentStatus.PENDING;
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = DocumentStatus.COMPLETED;
        this.lastAnalyzedAt = Instant.now();
    }

    public void markFailed() {
        this.status = DocumentStatus.FAILED;
    }

    public void markPendingForReanalysis() {
        this.status = DocumentStatus.PENDING;
    }
}
