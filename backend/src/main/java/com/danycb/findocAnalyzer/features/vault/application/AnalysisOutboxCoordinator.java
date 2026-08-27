package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.PublishAnalysisOutboxUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class AnalysisOutboxCoordinator {
    private final PublishAnalysisOutboxUseCase publisher;
    private final Executor executor;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Duration retryDelay;
    private final Duration maxRetryDelay;
    private final AtomicBoolean drainRequested = new AtomicBoolean();
    private final AtomicBoolean draining = new AtomicBoolean();

    public AnalysisOutboxCoordinator(
            PublishAnalysisOutboxUseCase publisher,
            @Qualifier("analysisOutboxExecutor") Executor executor,
            @Value("${findoc.analysis-outbox.batch-size:25}") int batchSize,
            @Value("${findoc.analysis-outbox.lease-duration:PT1M}") Duration leaseDuration,
            @Value("${findoc.analysis-outbox.retry-delay:PT30S}") Duration retryDelay,
            @Value("${findoc.analysis-outbox.max-retry-delay:PT15M}") Duration maxRetryDelay) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("analysis outbox batch size must be positive");
        }
        requirePositive(leaseDuration, "analysis outbox lease duration");
        requirePositive(retryDelay, "analysis outbox retry delay");
        requirePositive(maxRetryDelay, "analysis outbox maximum retry delay");
        if (maxRetryDelay.compareTo(retryDelay) < 0) {
            throw new IllegalArgumentException(
                    "analysis outbox maximum retry delay must not be shorter than its retry delay");
        }
        this.publisher = publisher;
        this.executor = executor;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.retryDelay = retryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    public void requestDrain() {
        drainRequested.set(true);
        scheduleIfIdle();
    }

    private void scheduleIfIdle() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::drainRequestedWork);
        } catch (RuntimeException failure) {
            draining.set(false);
            throw failure;
        }
    }

    private void drainRequestedWork() {
        try {
            do {
                drainRequested.set(false);
                drainAvailable();
            } while (drainRequested.get());
        } catch (RuntimeException failure) {
            log.error("event=analysis_outbox_drain_failed", failure);
        } finally {
            draining.set(false);
            if (drainRequested.get()) {
                scheduleIfIdle();
            }
        }
    }

    private void drainAvailable() {
        int processed;
        do {
            processed = publisher.publishDue(
                    Instant.now(), batchSize, leaseDuration, retryDelay, maxRetryDelay);
        } while (processed == batchSize);
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
