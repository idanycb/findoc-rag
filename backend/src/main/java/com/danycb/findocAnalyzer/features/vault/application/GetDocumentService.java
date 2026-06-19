package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.GetDocumentUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetDocumentService implements GetDocumentUseCase {
    private final DocumentRepositoryPort repository;

    @Override
    @Transactional(readOnly = true)
    public Document execute(UUID id) {
        return repository.getById(id);
    }
}
