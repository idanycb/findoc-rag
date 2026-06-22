package com.danycb.findocAnalyzer.features.vault.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentRepositoryPort;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DocumentRepository implements DocumentRepositoryPort {
    private final DocumentJpaRepository jpaRepository;

    @Override
    public Document save(Document document) {
        DocumentJpaEntity saved = jpaRepository.save(toEntity(document));
        return toDomain(saved);
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Document> findByTeamId(UUID teamId) {
        return jpaRepository.findByTeamId(teamId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Document> findByIdAndTeamId(UUID id, UUID teamId) {
        return jpaRepository.findByIdAndTeamId(id, teamId).map(this::toDomain);
    }

    @Override
    public void delete(Document document) {
        jpaRepository.deleteById(document.getId());
    }

    private DocumentJpaEntity toEntity(Document document) {
        return DocumentJpaEntity.builder()
                .id(document.getId())
                .teamId(document.getTeamId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .uploadedAt(document.getUploadedAt())
                .status(document.getStatus())
                .lastAnalyzedAt(document.getLastAnalyzedAt())
                .version(document.getVersion())
                .build();
    }

    private Document toDomain(DocumentJpaEntity entity) {
        return Document.builder()
                .id(entity.getId())
                .teamId(entity.getTeamId())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .contentType(entity.getContentType())
                .uploadedAt(entity.getUploadedAt())
                .status(entity.getStatus())
                .lastAnalyzedAt(entity.getLastAnalyzedAt())
                .version(entity.getVersion())
                .build();
    }
}

interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    List<DocumentJpaEntity> findByTeamId(UUID teamId);

    Optional<DocumentJpaEntity> findByIdAndTeamId(UUID id, UUID teamId);
}
