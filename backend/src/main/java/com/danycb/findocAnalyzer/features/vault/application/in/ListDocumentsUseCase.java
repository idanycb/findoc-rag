package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.domain.Document;

import java.util.List;

public interface ListDocumentsUseCase {
    List<Document> execute();
}
