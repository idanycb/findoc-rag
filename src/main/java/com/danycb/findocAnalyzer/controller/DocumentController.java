package com.danycb.findocAnalyzer.controller;

import com.danycb.findocAnalyzer.dto.DocumentRequestDTO;
import com.danycb.findocAnalyzer.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.model.DocumentMetadata;
import com.danycb.findocAnalyzer.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<DocumentResponseDTO>> getAll() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @PostMapping
    public ResponseEntity<DocumentResponseDTO> create(@Valid @RequestBody DocumentRequestDTO request, @AuthenticationPrincipal String username) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.saveDocumentMetadata(request,username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<DocumentResponseDTO> analyze(@PathVariable UUID id, @AuthenticationPrincipal String username) {
        DocumentResponseDTO result = documentService.analyzeDocument(id, username);
        return ResponseEntity.ok(result);
    }
}
