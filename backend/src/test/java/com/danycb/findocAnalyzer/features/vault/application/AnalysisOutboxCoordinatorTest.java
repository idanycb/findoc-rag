package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.PublishAnalysisOutboxUseCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisOutboxCoordinatorTest {
    private static final int BATCH_SIZE = 25;
    private static final Duration LEASE = Duration.ofMinutes(1);
    private static final Duration RETRY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY = Duration.ofMinutes(15);

    @Test
    void burstWakeupsScheduleOneWorkerAndDrainEveryAvailableBatch() {
        ManualExecutor executor = new ManualExecutor();
        RecordingPublisher publisher = new RecordingPublisher(BATCH_SIZE, BATCH_SIZE, 4);
        AnalysisOutboxCoordinator coordinator = new AnalysisOutboxCoordinator(
                publisher, executor, BATCH_SIZE, LEASE, RETRY, MAX_RETRY);

        coordinator.requestDrain();
        coordinator.requestDrain();
        coordinator.requestDrain();

        assertThat(executor.pendingTaskCount()).isEqualTo(1);
        executor.runNext();

        assertThat(publisher.limits).containsExactly(BATCH_SIZE, BATCH_SIZE, BATCH_SIZE);
        assertThat(executor.pendingTaskCount()).isZero();
    }

    @Test
    void wakeupDuringDrainCausesAnotherPassWithoutStartingAConcurrentWorker() {
        ManualExecutor executor = new ManualExecutor();
        List<Integer> limits = new ArrayList<>();
        AnalysisOutboxCoordinator[] coordinator = new AnalysisOutboxCoordinator[1];
        PublishAnalysisOutboxUseCase publisher = (now, limit, lease, retry, maxRetry) -> {
            limits.add(limit);
            if (limits.size() == 1) {
                coordinator[0].requestDrain();
            }
            return 0;
        };
        coordinator[0] = new AnalysisOutboxCoordinator(
                publisher, executor, BATCH_SIZE, LEASE, RETRY, MAX_RETRY);

        coordinator[0].requestDrain();
        executor.runNext();

        assertThat(limits).containsExactly(BATCH_SIZE, BATCH_SIZE);
        assertThat(executor.pendingTaskCount()).isZero();
    }

    @Test
    void unexpectedDrainFailureReleasesTheWorkerForTheNextRecoveryWakeup() {
        ManualExecutor executor = new ManualExecutor();
        List<Integer> attempts = new ArrayList<>();
        PublishAnalysisOutboxUseCase publisher = (now, limit, lease, retry, maxRetry) -> {
            attempts.add(limit);
            if (attempts.size() == 1) {
                throw new IllegalStateException("database unavailable");
            }
            return 0;
        };
        AnalysisOutboxCoordinator coordinator = new AnalysisOutboxCoordinator(
                publisher, executor, BATCH_SIZE, LEASE, RETRY, MAX_RETRY);

        coordinator.requestDrain();
        executor.runNext();
        coordinator.requestDrain();
        executor.runNext();

        assertThat(attempts).containsExactly(BATCH_SIZE, BATCH_SIZE);
        assertThat(executor.pendingTaskCount()).isZero();
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pendingTaskCount() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class RecordingPublisher implements PublishAnalysisOutboxUseCase {
        private final Queue<Integer> results = new ArrayDeque<>();
        private final List<Integer> limits = new ArrayList<>();

        private RecordingPublisher(Integer... results) {
            this.results.addAll(List.of(results));
        }

        @Override
        public int publishDue(
                Instant now, int limit, Duration leaseDuration,
                Duration retryDelay, Duration maxRetryDelay) {
            limits.add(limit);
            return results.remove();
        }
    }
}
