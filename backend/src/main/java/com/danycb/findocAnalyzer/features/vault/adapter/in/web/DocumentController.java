package com.danycb.findocAnalyzer.features.vault.adapter.in.web;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentUploadCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.UploadResult;
import com.danycb.findocAnalyzer.features.vault.application.in.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final ListDocumentsUseCase listDocuments;
    private final GetDocumentUseCase getDocument;
    private final InitiateUploadUseCase initiateUpload;
    private final GenerateViewUrlUseCase generateViewUrl;
    private final DeleteDocumentUseCase deleteDocument;
    private final RequestDocumentAnalysisUseCase requestAnalysis;

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailResponse> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(DocumentDetailResponse.from(getDocument.execute(id)));
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummaryResponse>> getAllDocuments() {
        return ResponseEntity.ok(
                listDocuments.execute().stream()
                        .map(DocumentSummaryResponse::from)
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<UploadResult> initiateUpload(
            @RequestBody @Valid DocumentUploadCommand uploadCommand) {
        UploadResult result = initiateUpload.execute(uploadCommand);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Map<String, String>> getDocumentViewUrl(@PathVariable UUID id) {
        String url = generateViewUrl.execute(id);
        return ResponseEntity.ok(Map.of("viewUrl", url));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<DocumentDetailResponse> requestDocumentAnalysis(@PathVariable UUID id) {
        return ResponseEntity.ok(DocumentDetailResponse.from(requestAnalysis.execute(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID id) {
        deleteDocument.execute(id);
    }

}
