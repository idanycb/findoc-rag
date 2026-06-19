package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.in.RequestDocumentAnalysisUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestDocumentAnalysisService implements RequestDocumentAnalysisUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;
    private final AnalysisQueuePort analysisQueue;

    @Override
    @Transactional
    public Document execute(UUID id) {
        Document document = repository.getById(id);

        if (document.cannotAnalyze()) {
            log.warn("Attempting reanalysis on Document {} in state {}", id, document.getStatus());
            return document;
        }

        document.markPendingForReanalysis();
        Document saved = repository.save(document);

        String objectKey = objectStorage.buildObjectKey(id);
        analysisQueue.enqueue(new DocumentAnalysisMessage(id, objectKey));

        return saved;
    }
}
