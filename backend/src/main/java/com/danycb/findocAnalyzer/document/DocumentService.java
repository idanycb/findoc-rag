package com.danycb.findocAnalyzer.document;

import com.danycb.findocAnalyzer.common.dto.DocumentRequestDTO;
import com.danycb.findocAnalyzer.common.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.common.exception.DocumentProcessingException;
import com.danycb.findocAnalyzer.common.exception.ResourceNotFoundException;
import com.danycb.findocAnalyzer.embeddings.VectorStoreService;
import com.danycb.findocAnalyzer.s3.S3EventPublisher;
import com.danycb.findocAnalyzer.s3.S3Service;
import com.danycb.findocAnalyzer.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentMetadataRepository repository;
    private final AsyncDocumentService asyncDocumentService;
    private final S3Service s3Service;
    private final S3EventPublisher s3EventPublisher;
    private final VectorStoreService vectorStoreService;

    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> getAllDocuments() {
        UUID tenantId = UserPrincipal.getCurrentTenantId();
        log.info("Fetching all document metadata for tenant: {}", tenantId);

        return repository.findAllByTenantId(tenantId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentResponseDTO getDocumentById(UUID id) {
        return mapToDTO(findDocById(id));
    }

    @Transactional
    public DocumentResponseDTO initiateDirectUpload(DocumentRequestDTO request) {
        UUID tenantId = UserPrincipal.getCurrentTenantId();
        log.info("User [{}] requested upload ticket for: {}", UserPrincipal.getCurrentUserId(), request.getFileName());

        DocumentMetadata doc = DocumentMetadata.builder()
                .fileName(request.getFileName())
                .fileSize(request.getSize())
                .contentType(request.getType())
                .status(DocumentStatus.PENDING)
                .tenantId(tenantId)
                .build();

        DocumentMetadata saved = repository.save(doc);

        String uploadUrl = s3Service.generatePresignedUploadUrl(tenantId, saved.getId(), saved.getFileName(), saved.getContentType());

        DocumentResponseDTO response = mapToDTO(saved);
        response.setUploadUrl(uploadUrl);

        return response;
    }

    @Transactional(readOnly = true)
    public String generateViewUrl(UUID id) {
        UUID tenantId = UserPrincipal.getCurrentTenantId();
        DocumentMetadata doc = findDocById(id, tenantId);

        return s3Service.generatePresignedViewUrl(tenantId, doc.getId(), doc.getFileName());
    }

    @Transactional
    public void deleteDocument(UUID id) {
        UUID tenantId = UserPrincipal.getCurrentTenantId();
        DocumentMetadata doc = findDocById(id, tenantId);
        repository.delete(doc);
        log.warn("Metadata for {} removed.", id);

        vectorStoreService.deleteByDocumentId(id);

        s3Service.deleteFile(tenantId, id, doc.getFileName());
    }

    @Transactional
    public void analyzeDocument(UUID id, UUID tenantId, byte[] rawContent) {
        DocumentMetadata doc = findDocById(id, tenantId);

        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentProcessingException("Analysis already in progress for document: " + id);
        }

        if (doc.getStatus() == DocumentStatus.COMPLETED) {
            log.info("Document {} has already been analyzed. Re-analyzing...", id);
        }

        doc.setStatus(DocumentStatus.PROCESSING);
        repository.saveAndFlush(doc);

        asyncDocumentService.docAnalysis(doc, rawContent, tenantId);
    }

    @Transactional
    public DocumentResponseDTO reanalyzeDocument(UUID id) {
        UUID tenantId = UserPrincipal.getCurrentTenantId();
        DocumentMetadata doc = findDocById(id, tenantId);

        if (doc.getStatus() != DocumentStatus.FAILED && doc.getStatus() != DocumentStatus.PENDING) {
            log.warn("Attempting reanalysis on Document {} in state {}", id, doc.getStatus());
            return mapToDTO(doc);
        }

        doc.setStatus(DocumentStatus.PENDING);
        repository.saveAndFlush(doc);

        s3EventPublisher.sendToSQS(tenantId, id, doc.getFileName());
        return mapToDTO(doc);
    }

    private DocumentResponseDTO mapToDTO(DocumentMetadata entity) {
        return DocumentResponseDTO.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .contentType(entity.getContentType())
                .uploadedAt(entity.getUploadedAt())
                .status(entity.getStatus())
                .aiSummary(entity.getAiSummary())
                .build();
    }

    private DocumentMetadata findDocById(UUID id) {
        UUID tenantId = UserPrincipal.getCurrentTenantId();
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id + " for tenant: " + tenantId));
    }

    private DocumentMetadata findDocById(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id + " for tenant: " + tenantId));
    }
}
