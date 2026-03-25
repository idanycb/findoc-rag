package com.danycb.findocAnalyzer.service;

import com.danycb.findocAnalyzer.dto.DocumentRequestDTO;
import com.danycb.findocAnalyzer.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.exception.DocumentProcessingException;
import com.danycb.findocAnalyzer.model.DocumentMetadata;
import com.danycb.findocAnalyzer.model.DocumentStatus;
import com.danycb.findocAnalyzer.exception.ResourceNotFoundException;
import com.danycb.findocAnalyzer.repository.DocumentMetadataRepository;
import com.danycb.findocAnalyzer.security.AuthContext;
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
    private final AuthContext auth;
    private final S3EventPublisher s3EventPublisher;

    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> getAllDocuments() {
        String userId = auth.username();
        log.info("Fetching all document metadata for user: {}", userId);

        return repository.findAllByUserId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentResponseDTO getDocumentById(UUID id) {
        return mapToDTO(findDocById(id));
    }

    @Transactional
    public DocumentResponseDTO initiateDirectUpload(DocumentRequestDTO request) {
        String userId = auth.username();
        log.info("User [{}] requested upload ticket for: {}", userId, request.getFileName());

        DocumentMetadata doc = DocumentMetadata.builder()
                .fileName(request.getFileName())
                .fileSize(request.getSize())
                .contentType(request.getType())
                .status(DocumentStatus.PENDING)
                .userId(userId)
                .build();

        DocumentMetadata saved = repository.save(doc);

        String uploadUrl = s3Service.generatePresignedUploadUrl(userId, saved.getId(), saved.getFileName(), saved.getContentType());

        DocumentResponseDTO response = mapToDTO(saved);
        response.setUploadUrl(uploadUrl);

        return response;
    }

    @Transactional(readOnly = true)
    public String generateViewUrl(UUID id) {
        String userId = auth.username();
        DocumentMetadata doc = findDocById(id, userId);

        return s3Service.generatePresignedViewUrl(userId, doc.getId(), doc.getFileName());
    }

    @Transactional
    public void deleteDocument(UUID id) {
        String userId = auth.username();
        DocumentMetadata doc = findDocById(id, userId);
        repository.delete(doc);
        log.warn("Metadata for {} removed.", id);

        s3Service.deleteFile(userId, id, doc.getFileName());
    }

    @Transactional
    public void analyzeDocument(UUID id, String userId, byte[] rawContent) {
        DocumentMetadata doc = findDocById(id, userId);

        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentProcessingException("Analysis already in progress for document: " + id);
        }

        if (doc.getStatus() == DocumentStatus.COMPLETED) {
            log.info("Document {} has already been analyzed. Re-analyzing...", id);
        }

        doc.setStatus(DocumentStatus.PROCESSING);
        repository.saveAndFlush(doc);

        asyncDocumentService.docAnalysis(doc, rawContent, userId);
    }

    @Transactional
    public DocumentResponseDTO reanalyzeDocument(UUID id) {
        String userId = auth.username();
        DocumentMetadata doc = findDocById(id, userId);

        if (doc.getStatus() != DocumentStatus.FAILED && doc.getStatus() != DocumentStatus.PENDING) {
            log.warn("Attempting reanalysis on Document {} in state {}", id, doc.getStatus());
            return mapToDTO(doc);
        }

        doc.setStatus(DocumentStatus.PENDING);
        repository.saveAndFlush(doc);

        s3EventPublisher.sendToSQS(userId, id, doc.getFileName());
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
        String userId = auth.username();
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id + " for user: " + userId));
    }

    private DocumentMetadata findDocById(UUID id, String userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id + " for user: " + userId));
    }
}
