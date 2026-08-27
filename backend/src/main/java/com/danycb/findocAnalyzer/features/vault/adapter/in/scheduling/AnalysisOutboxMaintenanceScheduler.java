package com.danycb.findocAnalyzer.features.vault.adapter.in.scheduling;

import com.danycb.findocAnalyzer.features.vault.application.AnalysisOutboxMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisOutboxMaintenanceScheduler {
    private final AnalysisOutboxMaintenanceService maintenance;

    @Scheduled(fixedDelayString = "${findoc.analysis-outbox.status-interval:PT5M}")
    public void reportStuckRequests() {
        maintenance.reportStuckRequests();
    }

    @Scheduled(fixedDelayString = "${findoc.analysis-outbox.cleanup-interval:PT24H}")
    public void cleanupPublishedRequests() {
        maintenance.cleanupPublishedRequests();
    }
}
