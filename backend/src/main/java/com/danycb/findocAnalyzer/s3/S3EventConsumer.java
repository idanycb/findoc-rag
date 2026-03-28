package com.danycb.findocAnalyzer.s3;

import com.danycb.findocAnalyzer.document.DocumentService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotification;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotificationRecord;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3EventConsumer {

    private final S3Service s3Service;
    private final DocumentService documentService;

    private record FileKeyParts(UUID tenantId, UUID docId) {
    }

    @SqsListener(value = "${AWS_SQS_QUEUE_NAME}")
    private void onS3Event(String messageJson) {
        log.info("Received S3 Event Notification via SQS");

        try {
            S3EventNotification event = S3EventNotification.fromJson(messageJson);

            if (event.getRecords() == null || event.getRecords().isEmpty()) {
                log.warn("Received SQS message with no record. Skipping.");
                return;
            }

            S3EventNotificationRecord record = event.getRecords().getFirst();
            String s3Key = record.getS3().getObject().getKey();

            if (s3Key == null || s3Key.isBlank()) {
                log.warn("S3 Object key is missing in event. Skipping");
                return;
            }

            String decodedKey = URLDecoder.decode(s3Key, StandardCharsets.UTF_8);
            FileKeyParts parts = extractPartsFromKey(decodedKey);

            byte[] fileBytes = s3Service.downloadFile(decodedKey);
            documentService.analyzeDocument(parts.docId, parts.tenantId, fileBytes);
        } catch (Exception e) {
            log.error("SQS Pipeline Failure: {}", e.getMessage());
            throw new RuntimeException("Re-queuing message for retry", e);
        }
    }

    private FileKeyParts extractPartsFromKey(String key) {
        try {
            String[] parts = key.split("/");

            if (parts.length != 4) {
                throw new IllegalArgumentException();
            }

            UUID tenantId = UUID.fromString(parts[1]);
            UUID fileId = UUID.fromString(parts[2]);

            return new FileKeyParts(tenantId, fileId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid S3 Key format. Expected files/{tenantId}/{UUID}/filename");
        }
    }
}
