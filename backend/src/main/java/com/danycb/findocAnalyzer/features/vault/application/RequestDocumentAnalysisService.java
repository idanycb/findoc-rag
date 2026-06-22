package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
import com.danycb.findocAnalyzer.features.vault.application.in.RequestDocumentAnalysisUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestDocumentAnalysisService implements RequestDocumentAnalysisUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;
    private final AnalysisQueuePort analysisQueue;
    private final VaultAuditLogger auditLogger;

    @Override
    @Transactional
    public void execute(UUID id, UUID teamId) {
        Document document = repository.getByIdForTeam(id, teamId);

        if (document.cannotAnalyze()) {
            auditLogger.analysisSkipped(document, "status_not_analyzable");
            return;
        }

        document.markPendingForReanalysis();
        Document saved = repository.save(document);

        String objectKey = objectStorage.buildObjectKey(id);
        analysisQueue.enqueue(new DocumentAnalysisMessage(id, objectKey));
        auditLogger.analysisRequested(saved);
    }
}
