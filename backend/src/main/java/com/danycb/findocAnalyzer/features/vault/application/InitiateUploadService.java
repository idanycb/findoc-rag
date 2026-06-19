package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentUploadCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.UploadResult;
import com.danycb.findocAnalyzer.features.vault.application.in.InitiateUploadUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitiateUploadService implements InitiateUploadUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;

    @Override
    @Transactional
    public UploadResult execute(DocumentUploadCommand command) {
        log.info("Requested upload ticket for: {}", command.getFileName());

        Document document = Document.builder()
                .fileName(command.getFileName())
                .fileSize(command.getSize())
                .contentType(command.getType())
                .status(DocumentStatus.PENDING)
                .build();

        Document saved = repository.save(document);

        String uploadUrl = objectStorage.generateUploadUrl(
                saved.getId(), saved.getContentType());

        return new UploadResult(saved.getId(), saved.getFileName(), saved.getStatus().name(), uploadUrl);
    }
}
