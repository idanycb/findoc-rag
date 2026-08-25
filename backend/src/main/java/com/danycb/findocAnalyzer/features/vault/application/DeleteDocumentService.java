package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.DeleteDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.application.out.VectorIndexPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        if (!document.isAmendment() && document.getAccessionNumber() != null) {
            repository.findByTeamIdAndAmendsAccessionNumber(teamId, document.getAccessionNumber())
                    .forEach(amendment -> {
                        amendment.reconcileAmendmentReference(
                                amendment.getAmendsAccessionNumber(), null);
                        repository.save(amendment);
                    });
        }

        repository.delete(document);
        auditLogger.documentDeleted(document);

        vectorIndex.deleteByDocumentId(id);
        deleteStorageAfterCommit(id);
    }

    private void deleteStorageAfterCommit(UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            objectStorage.delete(id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectStorage.delete(id);
            }
        });
    }
}
