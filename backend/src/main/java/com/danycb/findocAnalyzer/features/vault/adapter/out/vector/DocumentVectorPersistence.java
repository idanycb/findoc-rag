package com.danycb.findocAnalyzer.features.vault.adapter.out.vector;

import com.danycb.findocAnalyzer.features.vault.domain.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.UUID;

/** Adapter-owned capability for atomic document replacement and amendment-family reconciliation. */
public interface DocumentVectorPersistence {
    void replaceDocument(List<Embedding> embeddings, List<TextSegment> segments, Document document);

    void deleteDocument(UUID documentId);
}
