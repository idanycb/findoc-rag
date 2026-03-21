package com.danycb.findocAnalyzer.repository;

import com.danycb.findocAnalyzer.model.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {
    List<DocumentMetadata> findAllByUserId(String userId);

    Optional<DocumentMetadata> findByIdAndUserId(UUID id, String userId);
}
