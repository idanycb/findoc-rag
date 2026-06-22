package com.danycb.findocAnalyzer.features.vault.adapter.in.messaging;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.in.AnalyzeDocumentUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotification;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotificationRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3UploadEventListener {

    private final AnalyzeDocumentUseCase analyzeDocumentUseCase;
    private final ObjectMapper objectMapper;

    @SqsListener(value = "${AWS_SQS_QUEUE_NAME}")
    void onMessage(String messageJson) {
        DocumentAnalysisMessage message = normalize(messageJson);
        if (message == null) {
            return;
        }
        log.info("event=document_analysis_message_received documentId={}", message.documentId());

        try {
            analyzeDocumentUseCase.analyze(message.documentId(), message.objectKey());
        } catch (Exception e) {
            log.error(
                    "event=document_analysis_message_failed documentId={} exception={} reason={}",
                    message.documentId(), e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Re-queuing message for retry", e);
        }
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
        return new DocumentAnalysisMessage(parts.docId(), decodedKey);
    }
}
