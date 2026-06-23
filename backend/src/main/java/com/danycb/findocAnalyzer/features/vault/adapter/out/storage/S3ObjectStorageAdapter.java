package com.danycb.findocAnalyzer.features.vault.adapter.out.storage;

import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ObjectStorageAdapter implements ExternalStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${AWS_S3_BUCKET_NAME}")
    private String bucketName;

    @Override
    public String generateUploadUrl(UUID docId, String contentType, long contentLength) {
        String s3Key = buildObjectKey(docId);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    @Override
    public String generateViewUrl(UUID docId) {
        String s3Key = buildObjectKey(docId);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(request)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    @Override
    public byte[] download(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request)) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read S3 object: " + objectKey, e);
        }
    }

    @Override
    public void delete(UUID docId) {
        String key = buildObjectKey(docId);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.error(
                    "event=document_storage_delete_failed documentId={} exception={} reason={}",
                    docId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    @Override
    public String buildObjectKey(UUID docId) {
        return String.format("files/%s", docId);
    }
}
