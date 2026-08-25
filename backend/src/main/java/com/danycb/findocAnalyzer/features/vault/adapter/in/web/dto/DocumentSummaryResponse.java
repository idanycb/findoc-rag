package com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.AmendmentLinkStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        String fileName,
        Long fileSize,
        String contentType,
        Instant uploadedAt,
        DocumentStatus status,
        DocumentSource source,
        String cik,
        String ticker,
        String companyName,
        String formType,
        String baseFormType,
        boolean isAmendment,
        String amendsAccessionNumber,
        UUID amendsDocumentId,
        AmendmentLinkStatus amendmentLinkStatus,
        boolean searchable,
        String fiscalPeriod,
        LocalDate reportDate,
        LocalDate filingDate,
        String accessionNumber,
        String sourceUrl
) {
    public static DocumentSummaryResponse from(Document document) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getFileName(),
                document.getFileSize(),
                document.getContentType(),
                document.getUploadedAt(),
                document.getStatus(),
                document.getSource(),
                document.getCik(),
                document.getTicker(),
                document.getCompanyName(),
                document.getFormType(),
                document.getBaseFormType(),
                document.isAmendment(),
                document.getAmendsAccessionNumber(),
                document.getAmendsDocumentId(),
                document.getAmendmentLinkStatus(),
                document.isSearchable(),
                document.getFiscalPeriod(),
                document.getReportDate(),
                document.getFilingDate(),
                document.getAccessionNumber(),
                document.getSourceUrl()
        );
    }
}
