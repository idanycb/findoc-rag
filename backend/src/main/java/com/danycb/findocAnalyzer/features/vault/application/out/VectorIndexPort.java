package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedPage;

import java.util.List;
import java.util.UUID;

public interface VectorIndexPort {
    void ingest(List<ParsedPage> pages, UUID docId, String fileName);

    void deleteByDocumentId(UUID docId);
}
