package com.danycb.findocAnalyzer.controller;

import com.danycb.findocAnalyzer.dto.DocumentResponseDTO;
import com.danycb.findocAnalyzer.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<DocumentResponseDTO> create(@RequestParam String fileName, @RequestParam Long size, @RequestParam String type) {
        return ResponseEntity.status(201).body(documentService.saveDocumentMetadata(fileName, size, type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }
}
