package com.danycb.findocAnalyzer.service;

import com.danycb.findocAnalyzer.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.exception.AiAnalysisException;
import com.danycb.findocAnalyzer.exception.DocumentProcessingException;
import com.danycb.findocAnalyzer.model.DocumentMetadata;
import com.danycb.findocAnalyzer.model.DocumentStatus;
import com.danycb.findocAnalyzer.exception.ResourceNotFoundException;
import com.danycb.findocAnalyzer.repository.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentMetadataRepository repository;
    private final DocumentAnalyzerEngine documentAnalyzerEngine;

    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> getAllDocuments() {
        log.info("Fetching all document metadata.");
        return repository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentResponseDTO saveDocumentMetadata(String fileName, Long size, String type) {
        log.debug("Saving metadata for file: {}", fileName);
        DocumentMetadata doc = DocumentMetadata.builder()
                .fileName(fileName)
                .fileSize(size)
                .contentType(type)
                .status(DocumentStatus.PENDING)
                .build();

        return mapToDTO(repository.save(doc));
    }

    @Transactional(readOnly = true)
    public DocumentResponseDTO getDocumentById(UUID id) {
        return repository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
    }

    @Transactional
    public DocumentResponseDTO analyzeDocument(UUID id) {
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

        try {
            log.debug("Initiating analysis for file: {}", doc.getFileName());
            String context = String.format("File: %s, Type: %s", doc.getFileName(), doc.getContentType());

            String aiSummary = documentAnalyzerEngine.analyzeMetadata(context);

            doc.setStatus(DocumentStatus.COMPLETED);
            doc.setAiSummary(aiSummary);
            doc.setLastAnalyzedAt(Instant.now());

            log.info("Successfully analyzed document: {}", doc.getId());
            return mapToDTO(repository.save(doc));
        } catch (Exception e) {
            log.error("AI analysis failed for doc: {}, error: {}", doc.getId(), e.getMessage());
            doc.setStatus(DocumentStatus.FAILED);
            repository.save(doc);

            throw new AiAnalysisException("Failed to process document via Groq:llama-3.1-8b-instant", e);
        }
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
