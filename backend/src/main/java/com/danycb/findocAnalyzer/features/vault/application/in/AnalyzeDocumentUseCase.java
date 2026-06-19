package com.danycb.findocAnalyzer.features.vault.application.in;

import java.util.UUID;

public interface AnalyzeDocumentUseCase {
    void analyze(UUID docId, String objectKey);
}
