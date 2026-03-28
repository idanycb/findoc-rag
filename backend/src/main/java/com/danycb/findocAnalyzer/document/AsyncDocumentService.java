package com.danycb.findocAnalyzer.document;

import com.danycb.findocAnalyzer.common.exception.AiAnalysisException;
import com.danycb.findocAnalyzer.embeddings.VectorStoreService;
import com.danycb.findocAnalyzer.llm.AiEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDocumentService {
    private final AiEngine aiEngine;
    private final DocumentMetadataRepository repository;
    private final VectorStoreService vectorStoreService;
    private final DocParserService docParserService;

    @Async
    public void docAnalysis(DocumentMetadata doc, byte[] rawContent, UUID tenantId) {
        try {
            log.debug("Initiating analysis for file: {} in Worker thread: {}", doc.getFileName(), Thread.currentThread().getName());
            doc.setStatus(DocumentStatus.PROCESSING);

            String metadata = String.format("File: %s, Type: %s, Size: %d bytes",
                    doc.getFileName(), doc.getContentType(), doc.getFileSize());

            String content = docParserService.extractTextFromPdf(rawContent);
            String contentSnippet = content.substring(0, Math.min(1500, content.length()));
            String aiSummary = aiEngine.analyzeDeepContent(
                    metadata, contentSnippet);

            vectorStoreService.ingestDocument(content, doc.getId(), tenantId);
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
