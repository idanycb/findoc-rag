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
        log.info("Received analysis message via SQS");

        DocumentAnalysisMessage message = normalize(messageJson);
        if (message == null) {
            return;
        }

        try {
            analyzeDocumentUseCase.analyze(message.documentId(), message.objectKey());
        } catch (Exception e) {
            log.error("SQS Pipeline Failure: {}", e.getMessage());
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
            log.warn("Unable to parse SQS message. Skipping. Reason: {}", e.getMessage());
            return null;
        }
    }

    private DocumentAnalysisMessage fromS3Event(String messageJson) {
        S3EventNotification event = S3EventNotification.fromJson(messageJson);

        if (event.getRecords() == null || event.getRecords().isEmpty()) {
            log.warn("Received S3 event with no record. Skipping.");
            return null;
        }

        S3EventNotificationRecord record = event.getRecords().getFirst();
        String s3Key = record.getS3().getObject().getKey();

        if (s3Key == null || s3Key.isBlank()) {
            log.warn("S3 Object key is missing in event. Skipping.");
            return null;
        }

        String decodedKey = URLDecoder.decode(s3Key, StandardCharsets.UTF_8);
        S3ObjectKeyParser.KeyParts parts = S3ObjectKeyParser.parse(decodedKey);
        return new DocumentAnalysisMessage(parts.docId(), decodedKey);
    }
}
