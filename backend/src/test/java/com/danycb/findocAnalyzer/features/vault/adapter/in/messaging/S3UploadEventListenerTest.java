package com.danycb.findocAnalyzer.features.vault.adapter.in.messaging;

import com.danycb.findocAnalyzer.features.vault.application.in.AnalyzeDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisRequestReceiptPort;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3UploadEventListenerTest {
    private final RecordingAnalyzeDocumentUseCase useCase = new RecordingAnalyzeDocumentUseCase();
    private final RecordingReceipts receipts = new RecordingReceipts();
    private final S3UploadEventListener listener = new S3UploadEventListener(
            useCase, receipts, new ObjectMapper(), Duration.ofMinutes(30));

    @Test
    void onMessage_directDocumentAnalysisMessage_invokesAnalyzeWithDocumentIdAndObjectKey() {
        UUID docId = UUID.randomUUID();
        String json = """
                {"documentId":"%s","objectKey":"files/upload.pdf"}
                """.formatted(docId);

        listener.onMessage(json);

        assertThat(useCase.receivedDocId).isEqualTo(docId);
        assertThat(useCase.receivedObjectKey).isEqualTo("files/upload.pdf");
    }

    @Test
    void onMessage_s3EventNotification_parsesKeyAndInvokesAnalyze() {
        UUID docId = UUID.randomUUID();
        String json = """
                {"Records":[{"s3":{"object":{"key":"files/%s"}}}]}
                """.formatted(docId);

        listener.onMessage(json);

        assertThat(useCase.receivedDocId).isEqualTo(docId);
        assertThat(useCase.receivedObjectKey).isEqualTo("files/" + docId);
    }

    @Test
    void onMessage_s3EventNotificationWithUrlEncodedKey_decodesBeforeParsing() {
        UUID docId = UUID.randomUUID();
        String json = """
                {"Records":[{"s3":{"object":{"key":"files%%2F%s"}}}]}
                """.formatted(docId);

        listener.onMessage(json);

        assertThat(useCase.receivedDocId).isEqualTo(docId);
        assertThat(useCase.receivedObjectKey).isEqualTo("files/" + docId);
    }

    @Test
    void onMessage_invalidJson_isSkippedWithoutThrowing() {
        listener.onMessage("not valid json {{{");

        assertThat(useCase.called).isFalse();
    }

    @Test
    void onMessage_s3EventWithEmptyRecords_isSkippedWithoutThrowing() {
        listener.onMessage("""
                {"Records":[]}
                """);

        assertThat(useCase.called).isFalse();
    }

    @Test
    void onMessage_analyzeThrows_wrapsAndRethrowsRuntimeException() {
        UUID docId = UUID.randomUUID();
        useCase.failWith = new IllegalStateException("boom");
        String json = """
                {"documentId":"%s","objectKey":"files/upload.pdf"}
                """.formatted(docId);

        assertThatThrownBy(() -> listener.onMessage(json))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void requestIdIsClaimedAndCompletedAroundAnalysis() {
        UUID requestId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String json = """
                {"requestId":"%s","documentId":"%s","objectKey":"files/upload.pdf"}
                """.formatted(requestId, docId);

        listener.onMessage(json);

        assertThat(receipts.claimedRequestId).isEqualTo(requestId);
        assertThat(receipts.claimedDocumentId).isEqualTo(docId);
        assertThat(receipts.completedRequestId).isEqualTo(requestId);
        assertThat(useCase.called).isTrue();
    }

    @Test
    void alreadyClaimedRequestIsSkippedAsADuplicate() {
        receipts.claimGranted = false;
        UUID requestId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        listener.onMessage("""
                {"requestId":"%s","documentId":"%s","objectKey":null}
                """.formatted(requestId, docId));

        assertThat(useCase.called).isFalse();
        assertThat(receipts.completedRequestId).isNull();
    }

    @Test
    void failedAnalysisReleasesItsFencedProcessingClaim() {
        useCase.failWith = new IllegalStateException("boom");
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> listener.onMessage("""
                {"requestId":"%s","documentId":"%s","objectKey":null}
                """.formatted(requestId, UUID.randomUUID())))
                .isInstanceOf(RuntimeException.class);

        assertThat(receipts.releasedRequestId).isEqualTo(requestId);
        assertThat(receipts.releaseError).contains("boom");
    }

    static class RecordingAnalyzeDocumentUseCase implements AnalyzeDocumentUseCase {
        boolean called;
        UUID receivedDocId;
        String receivedObjectKey;
        RuntimeException failWith;

        @Override
        public void analyze(UUID docId, String objectKey) {
            called = true;
            receivedDocId = docId;
            receivedObjectKey = objectKey;
            if (failWith != null) {
                throw failWith;
            }
        }
    }

    static class RecordingReceipts implements AnalysisRequestReceiptPort {
        private final UUID claimToken = UUID.randomUUID();
        boolean claimGranted = true;
        UUID claimedRequestId;
        UUID claimedDocumentId;
        UUID completedRequestId;
        UUID releasedRequestId;
        String releaseError;

        @Override
        public Optional<ProcessingClaim> claim(
                UUID requestId, UUID documentId, Instant now, Duration leaseDuration) {
            claimedRequestId = requestId;
            claimedDocumentId = documentId;
            return claimGranted
                    ? Optional.of(new ProcessingClaim(requestId, claimToken))
                    : Optional.empty();
        }

        @Override
        public void markProcessed(UUID requestId, UUID claimToken, Instant processedAt) {
            completedRequestId = requestId;
        }

        @Override
        public void release(UUID requestId, UUID claimToken, String error) {
            releasedRequestId = requestId;
            releaseError = error;
        }
    }
}
