package com.danycb.findocAnalyzer.features.vault.application.in;

import java.util.UUID;

public interface DeleteDocumentUseCase {
    void execute(UUID id, UUID teamId);
}
