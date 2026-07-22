package com.danycb.findocAnalyzer.features.vault.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTest {

    private Document document(DocumentStatus status) {
        return Document.builder()
                .id(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .fileName("report.pdf")
                .status(status)
                .build();
    }

    @Test
    void markProcessing_setsStatusToProcessing() {
        Document document = document(DocumentStatus.PENDING);

        document.markProcessing();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void markCompleted_setsStatusToCompletedAndStampsLastAnalyzedAt() {
        Document document = document(DocumentStatus.PROCESSING);

        document.markCompleted();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.getLastAnalyzedAt()).isNotNull();
    }

    @Test
    void markFailed_setsStatusToFailed() {
        Document document = document(DocumentStatus.PROCESSING);

        document.markFailed();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void markPendingForReanalysis_setsStatusToPending() {
        Document document = document(DocumentStatus.COMPLETED);

        document.markPendingForReanalysis();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void cannotAnalyze_isFalseForPendingAndFailed() {
        assertThat(document(DocumentStatus.PENDING).cannotAnalyze()).isFalse();
        assertThat(document(DocumentStatus.FAILED).cannotAnalyze()).isFalse();
    }

    @Test
    void cannotAnalyze_isTrueForCompletedAndProcessing() {
        assertThat(document(DocumentStatus.COMPLETED).cannotAnalyze()).isTrue();
        assertThat(document(DocumentStatus.PROCESSING).cannotAnalyze()).isTrue();
    }

    @Test
    void cannotAnalyze_isTrueWhenStatusIsNull() {
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .fileName("report.pdf")
                .build();

        assertThat(document.cannotAnalyze()).isTrue();
    }

    @Test
    void getSource_defaultsToUploadWhenNull() {
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .fileName("report.pdf")
                .status(DocumentStatus.PENDING)
                .source(null)
                .build();

        assertThat(document.getSource()).isEqualTo(DocumentSource.UPLOAD);
    }

    @Test
    void getSource_returnsSetValue() {
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .fileName("AAPL 10-K")
                .status(DocumentStatus.PENDING)
                .source(DocumentSource.EDGAR)
                .build();

        assertThat(document.getSource()).isEqualTo(DocumentSource.EDGAR);
    }
}
