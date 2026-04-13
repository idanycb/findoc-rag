package com.danycb.findocAnalyzer.document;

import com.danycb.findocAnalyzer.document.dto.DocumentRequestDTO;
import com.danycb.findocAnalyzer.document.dto.DocumentResponseDTO;
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
    public ResponseEntity<DocumentResponseDTO> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable("id") UUID id) {
        documentService.deleteDocument(id);
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Map<String, Object>> getViewUrl(@PathVariable("id") UUID id) {
        String url = documentService.generateViewUrl(id);
        return ResponseEntity.ok(Map.of("viewUrl", url));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<DocumentResponseDTO> analyze(@PathVariable("id") UUID id) {
        DocumentResponseDTO result = documentService.reanalyzeDocument(id);
        return ResponseEntity.ok(result);
    }
}
