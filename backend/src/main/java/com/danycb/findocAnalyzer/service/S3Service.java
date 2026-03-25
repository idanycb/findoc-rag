package com.danycb.findocAnalyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
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
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${AWS_S3_BUCKET_NAME}")
    public String bucketName;

    public String generatePresignedUploadUrl(String userId, UUID docId, String fileName, String contentType) {
        String s3Key = buildS3Key(userId, docId, fileName);
        log.debug("Generating upload url for key: {}", s3Key);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public String generatePresignedViewUrl(String userId, UUID docId, String fileName) {
        String s3Key = buildS3Key(userId, docId, fileName);
        log.debug("Generating view url for key: {}", s3Key);

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

    public byte[] downloadFile(String s3Key) {
        log.info("Downloading file from S3; {}",s3Key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(request);

        return responseBytes.asByteArray();
    }

    public void deleteFile(String userId, UUID docId, String fileName) {
        String key = buildS3Key(userId, docId, fileName);
        deleteObjectByKey(key);
    }

    public void deleteObjectByKey(String key) {
        log.info("Requesting S3 deletion for key: {}",key);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete S3 object {}: {}", key, e.getMessage());
        }
    }

    public String buildS3Key(String userId, UUID docId, String fileName) {
        return String.format("files/%s/%s/%s", userId, docId, fileName);
    }
}
