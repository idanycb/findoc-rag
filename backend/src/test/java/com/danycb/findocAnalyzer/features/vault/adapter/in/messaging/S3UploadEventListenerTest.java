package com.danycb.findocAnalyzer.features.vault.adapter.in.messaging;

import com.danycb.findocAnalyzer.features.vault.application.in.AnalyzeDocumentUseCase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3UploadEventListenerTest {
    private final RecordingAnalyzeDocumentUseCase useCase = new RecordingAnalyzeDocumentUseCase();
    private final S3UploadEventListener listener = new S3UploadEventListener(useCase, new ObjectMapper());

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
}
