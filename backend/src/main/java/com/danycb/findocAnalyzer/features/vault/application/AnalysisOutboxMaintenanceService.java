package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.GetAnalysisOutboxStatusUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class AnalysisOutboxMaintenanceService implements GetAnalysisOutboxStatusUseCase {
    private final AnalysisOutboxMaintenancePort outbox;
    private final Duration retention;
    private final int cleanupBatchSize;
    private final Duration stuckAfter;
    private final int statusDetailLimit;

    public AnalysisOutboxMaintenanceService(
            AnalysisOutboxMaintenancePort outbox,
            @Value("${findoc.analysis-outbox.retention:P30D}") Duration retention,
            @Value("${findoc.analysis-outbox.cleanup-batch-size:500}") int cleanupBatchSize,
            @Value("${findoc.analysis-outbox.stuck-after:PT15M}") Duration stuckAfter,
            @Value("${findoc.analysis-outbox.status-detail-limit:20}") int statusDetailLimit) {
        requirePositive(retention, "analysis outbox retention");
        requirePositive(stuckAfter, "analysis outbox stuck threshold");
        if (cleanupBatchSize <= 0) {
            throw new IllegalArgumentException("analysis outbox cleanup batch size must be positive");
        }
        if (statusDetailLimit <= 0) {
            throw new IllegalArgumentException("analysis outbox status detail limit must be positive");
        }
        this.outbox = outbox;
        this.retention = retention;
        this.cleanupBatchSize = cleanupBatchSize;
        this.stuckAfter = stuckAfter;
        this.statusDetailLimit = statusDetailLimit;
    }

    @Override
    public OutboxStatus getStatus() {
        Instant now = Instant.now();
        return outbox.inspect(now, now.minus(stuckAfter), statusDetailLimit);
    }

    public void reportStuckRequests() {
        OutboxStatus status = getStatus();
        if (status.stuckPublicationCount() == 0 && status.stuckProcessingCount() == 0) {
            return;
        }
        log.warn(
                "event=analysis_outbox_stuck_requests stuckPublicationCount={} "
                        + "stuckProcessingCount={} oldestPendingAt={} maxAttemptCount={}",
                status.stuckPublicationCount(),
                status.stuckProcessingCount(),
                status.oldestPendingAt(),
                status.maxAttemptCount());
    }

    public int cleanupPublishedRequests() {
        Instant cutoff = Instant.now().minus(retention);
        int deleted = outbox.deletePublishedProcessedBefore(cutoff, cleanupBatchSize);
        if (deleted > 0) {
            log.info("event=analysis_outbox_cleanup deleted={} cutoff={}", deleted, cutoff);
        }
        return deleted;
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
