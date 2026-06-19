package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.domain.Document;

import java.util.UUID;

public interface RequestDocumentAnalysisUseCase {
    Document execute(UUID id);
}
