package com.danycb.findocAnalyzer.features.vault.adapter.in.web;

import com.danycb.findocAnalyzer.features.vault.application.in.GetAnalysisOutboxStatusUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort.OutboxStatus;
import com.danycb.findocAnalyzer.infra.security.RequireSuperAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analysis-outbox")
@RequireSuperAdmin
@RequiredArgsConstructor
public class AnalysisOutboxAdminController {
    private final GetAnalysisOutboxStatusUseCase getStatus;

    @GetMapping
    public OutboxStatus getStatus() {
        return getStatus.getStatus();
    }
}
