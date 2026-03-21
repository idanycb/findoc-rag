package com.danycb.findocAnalyzer.service;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3EventPublisher {
    private final S3Service s3Service;
    private final SqsTemplate sqsTemplate;

    @Value("${AWS_SQS_QUEUE_NAME}")
    private String queueName;

    public void sendToSQS(String userId, UUID docId, String fileName) {
        String s3Key = s3Service.buildS3Key(userId, docId, fileName);
        String bucketName = s3Service.bucketName;

        String syntheticS3EventJson = String.format("""
                {
                    "Records": [
                        {
                            "s3": {
                                "bucket": { "name": "%s" },
                                "object": { "key": "%s" }
                            }
                        }
                    ]
                }
                """, bucketName, s3Key);

        sqsTemplate.send(queueName, syntheticS3EventJson);

        log.info("Re-queuing analysis for doc {} via SQS", docId);
    }
}
