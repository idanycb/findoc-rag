package com.danycb.findocAnalyzer.features.vault.adapter.out.messaging;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
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

    @Value("${AWS_SQS_QUEUE_NAME}")
    private String queueName;

    @Override
    public void enqueue(DocumentAnalysisMessage message) {
        sqsTemplate.send(queueName, message);
        log.info("event=document_analysis_message_enqueued documentId={}", message.documentId());
    }
}
