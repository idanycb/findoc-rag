package com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentSource;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the web response mappers ({@code DocumentDetailResponse.from} /
 * {@code DocumentSummaryResponse.from}), covering the EDGAR filing metadata fields in particular.
 */
class DocumentResponseMappingTest {

    private Document edgarDocument() {
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .fileName("aapl-10k.pdf")
                .fileSize(2048L)
                .contentType("application/pdf")
                .uploadedAt(Instant.parse("2026-01-02T03:04:05Z"))
                .source(DocumentSource.EDGAR)
                .cik("320193")
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .formType("10-K")
                .fiscalPeriod("FY2024")
                .reportDate(LocalDate.of(2024, 9, 28))
                .filingDate(LocalDate.of(2024, 11, 1))
                .accessionNumber("0000320193-24-000123")
                .sourceUrl("https://sec.example/aapl")
                .build();
        document.markCompleted();
        return document;
    }

    @Test
    void detailResponse_mapsAllFieldsIncludingEdgarMetadata() {
        Document document = edgarDocument();

        DocumentDetailResponse response = DocumentDetailResponse.from(document);

        assertThat(response.id()).isEqualTo(document.getId());
        assertThat(response.fileName()).isEqualTo("aapl-10k.pdf");
        assertThat(response.fileSize()).isEqualTo(2048L);
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.uploadedAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(response.status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(response.lastAnalyzedAt()).isEqualTo(document.getLastAnalyzedAt());
        assertThat(response.source()).isEqualTo(DocumentSource.EDGAR);
        assertThat(response.cik()).isEqualTo("320193");
        assertThat(response.ticker()).isEqualTo("AAPL");
        assertThat(response.companyName()).isEqualTo("Apple Inc.");
        assertThat(response.formType()).isEqualTo("10-K");
        assertThat(response.fiscalPeriod()).isEqualTo("FY2024");
        assertThat(response.reportDate()).isEqualTo(LocalDate.of(2024, 9, 28));
        assertThat(response.filingDate()).isEqualTo(LocalDate.of(2024, 11, 1));
        assertThat(response.accessionNumber()).isEqualTo("0000320193-24-000123");
        assertThat(response.sourceUrl()).isEqualTo("https://sec.example/aapl");
    }

    @Test
    void summaryResponse_mapsAllFieldsIncludingEdgarMetadata() {
        Document document = edgarDocument();

        DocumentSummaryResponse response = DocumentSummaryResponse.from(document);

        assertThat(response.id()).isEqualTo(document.getId());
        assertThat(response.fileName()).isEqualTo("aapl-10k.pdf");
        assertThat(response.fileSize()).isEqualTo(2048L);
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.uploadedAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(response.status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(response.source()).isEqualTo(DocumentSource.EDGAR);
        assertThat(response.cik()).isEqualTo("320193");
        assertThat(response.ticker()).isEqualTo("AAPL");
        assertThat(response.companyName()).isEqualTo("Apple Inc.");
        assertThat(response.formType()).isEqualTo("10-K");
        assertThat(response.fiscalPeriod()).isEqualTo("FY2024");
        assertThat(response.reportDate()).isEqualTo(LocalDate.of(2024, 9, 28));
        assertThat(response.filingDate()).isEqualTo(LocalDate.of(2024, 11, 1));
        assertThat(response.accessionNumber()).isEqualTo("0000320193-24-000123");
        assertThat(response.sourceUrl()).isEqualTo("https://sec.example/aapl");
    }

    @Test
    void defaultsToUploadSourceForPlainUploads() {
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .fileName("upload.pdf")
                .build();

        assertThat(DocumentDetailResponse.from(document).source()).isEqualTo(DocumentSource.UPLOAD);
        assertThat(DocumentSummaryResponse.from(document).source()).isEqualTo(DocumentSource.UPLOAD);
    }
}
