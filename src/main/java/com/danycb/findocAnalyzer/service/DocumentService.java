package com.danycb.findocAnalyzer.service;

import com.danycb.findocAnalyzer.dto.DocumentResponseDTO;
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
