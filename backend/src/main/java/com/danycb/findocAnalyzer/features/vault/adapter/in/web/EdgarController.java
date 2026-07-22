package com.danycb.findocAnalyzer.features.vault.adapter.in.web;

import com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto.ImportFilingRequest;
import com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto.ImportFilingResponse;
import com.danycb.findocAnalyzer.features.vault.application.in.ImportFilingUseCase;
import com.danycb.findocAnalyzer.features.vault.application.in.ListFilingsUseCase;
import com.danycb.findocAnalyzer.features.vault.application.in.SearchCompaniesUseCase;
import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;
import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;
import com.danycb.findocAnalyzer.infra.security.RequireTeamMember;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/edgar")
@RequireTeamMember
@RequiredArgsConstructor
public class EdgarController {
    private final SearchCompaniesUseCase searchCompanies;
    private final ListFilingsUseCase listFilings;
    private final ImportFilingUseCase importFiling;

    @GetMapping("/companies")
    public List<CompanyResult> searchCompanies(@RequestParam("q") String query) {
        return searchCompanies.search(query);
    }

    @GetMapping("/companies/{id}/filings")
    public List<FilingResult> listFilings(
            @PathVariable String id,
            @RequestParam(value = "type", required = false) String type) {
        return listFilings.list(id, type);
    }

    @PostMapping("/filings/import")
    public ResponseEntity<ImportFilingResponse> importFiling(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid ImportFilingRequest request) {
        var result = importFiling.importFiling(request.toCommand(), principal.teamId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ImportFilingResponse(result.documentId(), result.fileName(), result.status().name()));
    }
}
