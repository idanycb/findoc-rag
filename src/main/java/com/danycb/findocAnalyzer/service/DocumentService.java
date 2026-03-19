package com.danycb.findocAnalyzer.service;

import com.danycb.findocAnalyzer.dto.DocumentRequestDTO;
import com.danycb.findocAnalyzer.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.exception.DocumentProcessingException;
import com.danycb.findocAnalyzer.model.DocumentMetadata;
import com.danycb.findocAnalyzer.model.DocumentStatus;
import com.danycb.findocAnalyzer.exception.ResourceNotFoundException;
import com.danycb.findocAnalyzer.repository.DocumentMetadataRepository;
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

    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> getAllDocuments() {
        log.info("Fetching all document metadata.");
        return repository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentResponseDTO saveDocumentMetadata(DocumentRequestDTO request, String userId) {
        String fileName = request.getFileName();
        Long size = request.getSize();
        String type = request.getType();

        log.debug("Ingesting document: {} for user: {}", fileName, userId);
        DocumentMetadata doc = DocumentMetadata.builder()
                .fileName(fileName)
                .fileSize(size)
                .contentType(type)
                .status(DocumentStatus.PENDING)
                .build();

        DocumentMetadata savedDoc = repository.save(doc);

        analyzeDocument(savedDoc.getId(), userId);

        return mapToDTO(savedDoc);
    }

    @Transactional(readOnly = true)
    public DocumentResponseDTO getDocumentById(UUID id) {
        return repository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
    }

    @Transactional
    public DocumentResponseDTO analyzeDocument(UUID id, String userId) {
        DocumentMetadata doc = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentProcessingException("Analysis already in progress for document: " + id);
        }

        if (doc.getStatus() == DocumentStatus.COMPLETED) {
            log.info("Document {} has already been analyzed. Re-analyzing...", id);
        }

        doc.setStatus(DocumentStatus.PROCESSING);
        repository.saveAndFlush(doc);

        // OCR retrieved raw text. Only for testing purpose
        String rawContent = "The company revenue for Q4 2023 was $12.5 million. Total expenses were $8.2 million.";

        asyncDocumentService.docAnalysisAsync(doc, rawContent, userId);

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
                .build();
    }
}
