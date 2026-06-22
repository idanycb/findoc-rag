package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.domain.Document;

import java.util.UUID;

public interface GetDocumentUseCase {
    Document execute(UUID id, UUID teamId);
}
