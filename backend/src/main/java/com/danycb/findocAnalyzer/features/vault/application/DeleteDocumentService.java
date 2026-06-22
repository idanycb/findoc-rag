package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.DeleteDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteDocumentService implements DeleteDocumentUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;
    private final VectorIndexPort vectorIndex;
    private final VaultAuditLogger auditLogger;

    @Override
    @Transactional
    public void execute(UUID id, UUID teamId) {
        Document document = repository.getByIdForTeam(id, teamId);

        repository.delete(document);
        auditLogger.documentDeleted(document);

        vectorIndex.deleteByDocumentId(id);
        objectStorage.delete(id);
    }
}
