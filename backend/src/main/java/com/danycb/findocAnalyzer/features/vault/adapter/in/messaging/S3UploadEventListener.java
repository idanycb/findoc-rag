package com.danycb.findocAnalyzer.features.vault.adapter.in.messaging;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.in.AnalyzeDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisRequestReceiptPort;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotification;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotificationRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class S3UploadEventListener {

    private final AnalyzeDocumentUseCase analyzeDocumentUseCase;
    private final AnalysisRequestReceiptPort receipts;
    private final ObjectMapper objectMapper;
    private final Duration processingLease;

    public S3UploadEventListener(
            AnalyzeDocumentUseCase analyzeDocumentUseCase,
            AnalysisRequestReceiptPort receipts,
            ObjectMapper objectMapper,
            @Value("${findoc.analysis-outbox.processing-lease:PT30M}") Duration processingLease) {
        if (processingLease == null || processingLease.isZero() || processingLease.isNegative()) {
            throw new IllegalArgumentException("analysis processing lease must be positive");
        }
        this.analyzeDocumentUseCase = analyzeDocumentUseCase;
        this.receipts = receipts;
        this.objectMapper = objectMapper;
        this.processingLease = processingLease;
    }

    @SqsListener(value = "${AWS_SQS_QUEUE_NAME}")
    void onMessage(String messageJson) {
        DocumentAnalysisMessage message = normalize(messageJson);
        if (message == null) {
            return;
        }
        AnalysisRequestReceiptPort.ProcessingClaim processingClaim = claim(message);
        if (message.requestId() != null && processingClaim == null) {
            log.info("event=document_analysis_message_skipped reason=duplicate_request requestId={} documentId={}",
                    message.requestId(), message.documentId());
            return;
        }
        log.info("event=document_analysis_message_received requestId={} documentId={}",
                message.requestId(), message.documentId());

        try {
            analyzeDocumentUseCase.analyze(message.documentId(), message.objectKey());
            if (processingClaim != null) {
                receipts.markProcessed(
                        processingClaim.requestId(), processingClaim.claimToken(), Instant.now());
            }
        } catch (Exception e) {
            if (processingClaim != null) {
                try {
                    receipts.release(
                            processingClaim.requestId(), processingClaim.claimToken(), conciseError(e));
                } catch (RuntimeException releaseFailure) {
                    e.addSuppressed(releaseFailure);
                }
            }
            log.error(
                    "event=document_analysis_message_failed requestId={} documentId={} exception={} reason={}",
                    message.requestId(), message.documentId(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Re-queuing message for retry", e);
        }
    }

    private AnalysisRequestReceiptPort.ProcessingClaim claim(DocumentAnalysisMessage message) {
        UUID requestId = message.requestId();
        if (requestId == null) {
            return null;
        }
        return receipts.claim(requestId, message.documentId(), Instant.now(), processingLease)
                .orElse(null);
    }

    private String conciseError(Exception failure) {
        String message = failure.getMessage();
        String error = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
        return error.substring(0, Math.min(error.length(), 1000));
    }

    private DocumentAnalysisMessage normalize(String messageJson) {
        try {
            JsonNode root = objectMapper.readTree(messageJson);
            if (root.has("Records")) {
                return fromS3Event(messageJson);
            }
            return objectMapper.treeToValue(root, DocumentAnalysisMessage.class);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn(
                    "event=document_analysis_message_skipped reason=invalid_payload exception={} detail={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private DocumentAnalysisMessage fromS3Event(String messageJson) {
        S3EventNotification event = S3EventNotification.fromJson(messageJson);

        if (event.getRecords() == null || event.getRecords().isEmpty()) {
            log.warn("event=document_analysis_message_skipped reason=missing_s3_record");
            return null;
        }

        S3EventNotificationRecord record = event.getRecords().getFirst();
        String s3Key = record.getS3().getObject().getKey();

        if (s3Key == null || s3Key.isBlank()) {
            log.warn("event=document_analysis_message_skipped reason=missing_s3_object_key");
            return null;
        }

        String decodedKey = URLDecoder.decode(s3Key, StandardCharsets.UTF_8);
        S3ObjectKeyParser.KeyParts parts = S3ObjectKeyParser.parse(decodedKey);
        return new DocumentAnalysisMessage(null, parts.docId(), decodedKey);
    }
}
