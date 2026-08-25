package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.application.ResourceNotFoundException;
import com.danycb.findocAnalyzer.features.vault.domain.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepositoryPort {
    Document save(Document document);

    InsertResult insertOrGet(Document document);

    boolean claimAnalysisPublication(UUID documentId);

    void releaseAnalysisPublication(UUID documentId);

    Optional<Document> findById(UUID id);

    default Document getById(UUID id) {
        return findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found with id: " + id));
    }

    Optional<Document> findByIdAndTeamId(UUID id, UUID teamId);

    Optional<Document> findByTeamIdAndAccessionNumber(UUID teamId, String accessionNumber);

    List<Document> findByTeamIdAndAmendsAccessionNumber(UUID teamId, String accessionNumber);

    default Document getByIdForTeam(UUID id, UUID teamId) {
        return findByIdAndTeamId(id, teamId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found with id: " + id));
    }

    List<Document> findByTeamId(UUID teamId);

    long countAll();

    void delete(Document document);

    record InsertResult(Document document, boolean inserted) {
    }
}
