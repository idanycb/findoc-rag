package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;

import java.util.List;
import java.util.UUID;

public interface VectorIndexPort {
    void ingest(List<ParsedSection> sections, UUID docId, UUID teamId, String fileName);

    void deleteByDocumentId(UUID docId);
}
