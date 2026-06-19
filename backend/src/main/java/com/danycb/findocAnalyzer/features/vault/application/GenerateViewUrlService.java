package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.GenerateViewUrlUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.application.out.ExternalStoragePort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateViewUrlService implements GenerateViewUrlUseCase {
    private final DocumentRepositoryPort repository;
    private final ExternalStoragePort objectStorage;

    @Override
    @Transactional(readOnly = true)
    public String execute(UUID id) {
        Document document = repository.getById(id);
        return objectStorage.generateViewUrl(document.getId());
    }
}
