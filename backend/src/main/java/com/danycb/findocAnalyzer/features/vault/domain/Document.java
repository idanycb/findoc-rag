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
    private final String baseFormType;
    @Builder.Default
    private final boolean amendment = false;
    private final String fiscalPeriod;
    private LocalDate reportDate;
    private LocalDate filingDate;
    private final String accessionNumber;
    private final String sourceUrl;

    private String amendsAccessionNumber;
    private UUID amendsDocumentId;
    private AmendmentLinkStatus amendmentLinkStatus;
    @Builder.Default
    private boolean searchable = true;

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

    public void linkToOriginal(UUID originalDocumentId) {
        this.amendsDocumentId = originalDocumentId;
        this.amendmentLinkStatus = AmendmentLinkStatus.LINKED;
    }

    public void reconcileAmendmentReference(String originalAccessionNumber, UUID originalDocumentId) {
        this.amendsAccessionNumber = originalAccessionNumber;
        if (!amendment) {
            this.amendsDocumentId = null;
            this.amendmentLinkStatus = AmendmentLinkStatus.NOT_APPLICABLE;
        } else if (originalDocumentId == null) {
            this.amendsDocumentId = null;
            this.amendmentLinkStatus = AmendmentLinkStatus.UNRESOLVED;
        } else {
            this.amendsDocumentId = originalDocumentId;
            this.amendmentLinkStatus = AmendmentLinkStatus.LINKED;
        }
    }

    public void reconcileFilingDates(LocalDate filingDate, LocalDate reportDate) {
        if (filingDate != null) {
            this.filingDate = filingDate;
        }
        if (reportDate != null) {
            this.reportDate = reportDate;
        }
    }

    public void updateAmendmentReference(String originalAccessionNumber) {
        this.amendsAccessionNumber = originalAccessionNumber;
    }

    public void markSearchable(boolean searchable) {
        this.searchable = searchable;
    }
}
