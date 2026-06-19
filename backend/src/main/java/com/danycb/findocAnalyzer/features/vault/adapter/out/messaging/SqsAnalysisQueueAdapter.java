package com.danycb.findocAnalyzer.features.vault.adapter.out.messaging;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsAnalysisQueueAdapter implements AnalysisQueuePort {
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${AWS_SQS_QUEUE_NAME}")
    private String queueName;

    @Override
    public void enqueue(DocumentAnalysisMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            sqsTemplate.send(queueName, json);
            log.info("Queued analysis for doc {} via SQS", message.documentId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize analysis message for doc " + message.documentId(), e);
        }
    }
}
