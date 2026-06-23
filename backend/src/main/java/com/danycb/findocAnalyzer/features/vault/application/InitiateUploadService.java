package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.infra.config.DeploymentLimitsPort;
import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentUploadCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.UploadResult;
import com.danycb.findocAnalyzer.features.vault.application.in.InitiateUploadUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InitiateUploadService implements InitiateUploadUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;
    private final VaultAuditLogger auditLogger;
    private final DeploymentLimitsPort limits;

    @Override
    @Transactional
    public UploadResult execute(DocumentUploadCommand command, UUID teamId) {
        limits.assertFileSizeAllowed(command.getSize());
        limits.assertCanAddDocument(repository::countAll);

        Document document = Document.builder()
                .teamId(teamId)
                .fileName(command.getFileName())
                .fileSize(command.getSize())
                .contentType(command.getType())
                .status(DocumentStatus.PENDING)
                .build();

        Document saved = repository.save(document);

        String uploadUrl = objectStorage.generateUploadUrl(
                saved.getId(), saved.getContentType(), command.getSize());
        auditLogger.uploadInitiated(saved);

        return new UploadResult(saved.getId(), saved.getFileName(), saved.getStatus().name(), uploadUrl);
    }
}
