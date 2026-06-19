package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.application.ResourceNotFoundException;
import com.danycb.findocAnalyzer.features.vault.domain.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepositoryPort {
    Document save(Document document);

    Optional<Document> findById(UUID id);

    default Document getById(UUID id) {
        return findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found with id: " + id));
    }

    List<Document> findAll();

    void delete(Document document);
}
