package com.danycb.findocAnalyzer.features.vault.adapter.out.messaging;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
            log.info("event=document_analysis_message_enqueued documentId={}", message.documentId());
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize analysis message for doc " + message.documentId(), e);
        }
    }
}
