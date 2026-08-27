package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisOutboxMaintenanceServiceTest {
    @Test
    void statusUsesConfiguredStuckThresholdAndBoundedDetailLimit() {
        RecordingMaintenancePort port = new RecordingMaintenancePort();
        AnalysisOutboxMaintenanceService service = new AnalysisOutboxMaintenanceService(
                port, Duration.ofDays(30), 500, Duration.ofMinutes(15), 20);

        service.getStatus();

        assertThat(Duration.between(port.stuckBefore, port.now)).isEqualTo(Duration.ofMinutes(15));
        assertThat(port.detailLimit).isEqualTo(20);
    }

    @Test
    void cleanupUsesRetentionCutoffAndDeletesOnlyOneConfiguredBatch() {
        RecordingMaintenancePort port = new RecordingMaintenancePort();
        port.deleted = 37;
        AnalysisOutboxMaintenanceService service = new AnalysisOutboxMaintenanceService(
                port, Duration.ofDays(30), 500, Duration.ofMinutes(15), 20);
        Instant before = Instant.now().minus(Duration.ofDays(30)).minusSeconds(1);

        int deleted = service.cleanupPublishedRequests();

        assertThat(deleted).isEqualTo(37);
        assertThat(port.cleanupCutoff).isAfter(before);
        assertThat(port.cleanupLimit).isEqualTo(500);
    }

    private static final class RecordingMaintenancePort implements AnalysisOutboxMaintenancePort {
        Instant now;
        Instant stuckBefore;
        int detailLimit;
        Instant cleanupCutoff;
        int cleanupLimit;
        int deleted;

        @Override
        public OutboxStatus inspect(Instant now, Instant stuckBefore, int detailLimit) {
            this.now = now;
            this.stuckBefore = stuckBefore;
            this.detailLimit = detailLimit;
            return new OutboxStatus(now, 0, 0, 0, 0, null, 0, List.of());
        }

        @Override
        public int deletePublishedProcessedBefore(Instant cutoff, int limit) {
            cleanupCutoff = cutoff;
            cleanupLimit = limit;
            return deleted;
        }
    }
}
