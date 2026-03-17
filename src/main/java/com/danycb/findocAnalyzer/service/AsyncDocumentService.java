package com.danycb.findocAnalyzer.service;

import com.danycb.findocAnalyzer.exception.AiAnalysisException;
import com.danycb.findocAnalyzer.model.DocumentMetadata;
import com.danycb.findocAnalyzer.model.DocumentStatus;
import com.danycb.findocAnalyzer.repository.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDocumentService {
    private final DocumentAnalyzerEngine documentAnalyzerEngine;
    private final DocumentMetadataRepository repository;

    @Async
    public void processAiAnalysisAsync(DocumentMetadata doc) {
        try {
            log.debug("Initiating analysis for file: {} in Worker thread: {}", doc.getFileName(), Thread.currentThread().getName());

            String context = String.format("File: %s, Type: %s", doc.getFileName(), doc.getContentType());
            String aiSummary = documentAnalyzerEngine.analyzeMetadata(context);
            updateDocumentSuccess(doc, aiSummary);
        } catch (Exception e) {
            log.error("AI analysis failed for doc: {}, error: {}", doc.getId(), e.getMessage());
            updateDocumentFailed(doc);

            throw new AiAnalysisException("Failed to process document via Groq:llama-3.1-8b-instant", e);
        }
    }

    private void updateDocumentSuccess(DocumentMetadata doc, String aiSummary) {
        doc.setStatus(DocumentStatus.COMPLETED);
        doc.setAiSummary(aiSummary);
        doc.setLastAnalyzedAt(Instant.now());
        repository.save(doc);
    }

    private void updateDocumentFailed(DocumentMetadata doc) {
        doc.setStatus(DocumentStatus.FAILED);
        repository.save(doc);
    }
}
