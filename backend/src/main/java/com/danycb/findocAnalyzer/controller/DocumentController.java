package com.danycb.findocAnalyzer.controller;

import com.danycb.findocAnalyzer.dto.DocumentRequestDTO;
import com.danycb.findocAnalyzer.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.service.DocumentService;
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

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<DocumentResponseDTO>> getAll() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @PostMapping
    public ResponseEntity<DocumentResponseDTO> uploadDocument(@RequestBody @Valid DocumentRequestDTO documentRequestDTO) {
        DocumentResponseDTO response = documentService.initiateDirectUpload(documentRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Map<String, Object>> getViewUrl(@PathVariable UUID id) {
        String url = documentService.generateViewUrl(id);
        return ResponseEntity.ok(Map.of("viewUrl", url));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<DocumentResponseDTO> analyze(@PathVariable UUID id) {
        DocumentResponseDTO result = documentService.reanalyzeDocument(id);
        return ResponseEntity.ok(result);
    }
}
