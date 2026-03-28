package com.danycb.findocAnalyzer.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {
    List<DocumentMetadata> findAllByTenantId(UUID tenantId);

    Optional<DocumentMetadata> findByIdAndTenantId(UUID id, UUID tenantId);
}
