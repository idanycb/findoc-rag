package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.domain.Document;

import java.util.List;
import java.util.UUID;

public interface ListDocumentsUseCase {
    List<Document> execute(UUID teamId);
}
