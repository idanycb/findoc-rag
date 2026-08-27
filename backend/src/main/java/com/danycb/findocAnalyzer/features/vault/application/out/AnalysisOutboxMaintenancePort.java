package com.danycb.findocAnalyzer.features.vault.application.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalysisOutboxMaintenancePort {
    OutboxStatus inspect(Instant now, Instant stuckBefore, int detailLimit);

    int deletePublishedProcessedBefore(Instant cutoff, int limit);

    enum Stage {
        PUBLICATION,
        PROCESSING
    }

    record StuckRequest(
            UUID requestId,
            UUID documentId,
            Stage stage,
            Instant createdAt,
            int attemptCount,
            Instant nextAttemptAt,
            String lastError) {
    }

    record OutboxStatus(
            Instant inspectedAt,
            long pendingPublicationCount,
            long stuckPublicationCount,
            long pendingProcessingCount,
            long stuckProcessingCount,
            Instant oldestPendingAt,
            int maxAttemptCount,
            List<StuckRequest> stuckRequests) {
    }
}
