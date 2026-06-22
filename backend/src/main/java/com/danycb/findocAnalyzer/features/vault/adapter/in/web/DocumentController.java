package com.danycb.findocAnalyzer.features.vault.adapter.in.web;

import com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto.DocumentDetailResponse;
import com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto.DocumentSummaryResponse;
import com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto.DocumentViewUrlResponse;
import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentUploadCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.UploadResult;
import com.danycb.findocAnalyzer.features.vault.application.in.*;
import com.danycb.findocAnalyzer.infra.security.RequireTeamMember;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
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
@RequireTeamMember
@RequiredArgsConstructor
public class DocumentController {

    private final ListDocumentsUseCase listDocuments;
    private final GetDocumentUseCase getDocument;
    private final InitiateUploadUseCase initiateUpload;
    private final GenerateViewUrlUseCase generateViewUrl;
    private final DeleteDocumentUseCase deleteDocument;
    private final RequestDocumentAnalysisUseCase requestAnalysis;

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailResponse> getDocument(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(DocumentDetailResponse.from(getDocument.execute(id, principal.teamId())));
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummaryResponse>> getAllDocuments(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                listDocuments.execute(principal.teamId()).stream()
                        .map(DocumentSummaryResponse::from)
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<UploadResult> initiateUpload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid DocumentUploadCommand uploadCommand) {
        UploadResult result = initiateUpload.execute(uploadCommand, principal.teamId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<DocumentViewUrlResponse> getDocumentViewUrl(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        String url = generateViewUrl.execute(id, principal.teamId());
        return ResponseEntity.ok(new DocumentViewUrlResponse(url));
    }

    @PostMapping("/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestDocumentAnalysis(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        requestAnalysis.execute(id, principal.teamId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        deleteDocument.execute(id, principal.teamId());
    }
}
