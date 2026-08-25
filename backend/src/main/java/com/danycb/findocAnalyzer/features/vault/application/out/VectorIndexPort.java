package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.danycb.findocAnalyzer.features.vault.domain.Document;

import java.util.List;
import java.util.UUID;

public interface VectorIndexPort {
    void ingest(List<ParsedSection> sections, Document document);

    void deleteByDocumentId(UUID docId);
}
