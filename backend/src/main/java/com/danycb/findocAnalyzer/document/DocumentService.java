package com.danycb.findocAnalyzer.document;

import com.danycb.findocAnalyzer.common.exception.DocumentProcessingException;
import com.danycb.findocAnalyzer.common.exception.ResourceNotFoundException;
import com.danycb.findocAnalyzer.docParser.DocParserService;
import com.danycb.findocAnalyzer.docParser.UnstructuredResponseDTO;
import com.danycb.findocAnalyzer.document.dto.DocumentRequestDTO;
import com.danycb.findocAnalyzer.document.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.embeddings.VectorStoreService;
import com.danycb.findocAnalyzer.s3.S3EventPublisher;
import com.danycb.findocAnalyzer.s3.S3Service;
import com.danycb.findocAnalyzer.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentMetadataRepository repository;
    private final S3Service s3Service;
    private final S3EventPublisher s3EventPublisher;
    private final VectorStoreService vectorStoreService;
    private final DocParserService docParserService;

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

    public void analyzeDocument(UUID docId, UUID tenantId, byte[] rawContent) {
        DocumentMetadata doc = findDocById(docId, tenantId);

        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentProcessingException("Analysis already in progress for document: " + docId);
        }

        if (doc.getStatus() == DocumentStatus.COMPLETED) {
            log.info("Document {} has already been analyzed. Re-analyzing...", docId);
        }

        doc.setStatus(DocumentStatus.PROCESSING);
        repository.save(doc);

        docParserService.extractTextFromPdf(rawContent, doc.getFileName())
                .windowUntilChanged(UnstructuredResponseDTO::getPageNumber)
                .flatMap(pageFlux ->
                        pageFlux.collectList()
                                .map(pageElements -> pageElements.stream()
                                        .map(UnstructuredResponseDTO::getText)
                                        .filter(text -> text != null && !text.isBlank())
                                        .collect(Collectors.joining("\n\n")))
                )
                .collectList()
                .flatMap(allPages ->
                        Mono.fromRunnable(() -> {
                            log.debug("Ingesting Document {} into Vector Store", docId);
                            vectorStoreService.ingestDocument(allPages, docId, doc.getFileName(), tenantId);
                        })
                ).subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .doBeforeRetry(retrySignal -> log.warn("Retrying for Document {} into Vector Store", docId)))
                .doOnSuccess(v -> {
                    log.info("Document {} has been analyzed", docId);
                    DocumentMetadata latest = findDocById(docId, tenantId);
                    latest.setStatus(DocumentStatus.COMPLETED);
                    repository.save(latest);
                })
                .doOnError(e -> {
                    log.error("Failed to ingest Document {} into Vector Store: {}", docId, e.getMessage());
                    DocumentMetadata latest = findDocById(docId, tenantId);
                    latest.setStatus(DocumentStatus.FAILED);
                    repository.save(latest);
                })
                .then().block();
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
