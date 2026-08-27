package com.danycb.findocAnalyzer.features.vault.adapter.in.scheduling;

import com.danycb.findocAnalyzer.features.vault.application.AnalysisOutboxCoordinator;
import com.danycb.findocAnalyzer.features.vault.application.event.AnalysisOutboxEnqueuedEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisOutboxSchedulerTest {
    @Test
    void committedEventsAndSafetyPollsBothRequestADrain() {
        RecordingCoordinator coordinator = new RecordingCoordinator();
        AnalysisOutboxScheduler scheduler = new AnalysisOutboxScheduler(coordinator);

        scheduler.onOutboxEnqueued(new AnalysisOutboxEnqueuedEvent(UUID.randomUUID()));
        scheduler.recoverMissedWakeups();

        assertThat(coordinator.drainRequests).isEqualTo(2);
    }

    private static final class RecordingCoordinator extends AnalysisOutboxCoordinator {
        private int drainRequests;

        private RecordingCoordinator() {
            super((now, limit, lease, retry, maxRetry) -> 0, Runnable::run,
                    1, Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(15));
        }

        @Override
        public void requestDrain() {
            drainRequests++;
        }
    }
}
