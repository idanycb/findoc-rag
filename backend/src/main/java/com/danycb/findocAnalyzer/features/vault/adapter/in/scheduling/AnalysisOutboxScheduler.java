package com.danycb.findocAnalyzer.features.vault.adapter.in.scheduling;

import com.danycb.findocAnalyzer.features.vault.application.AnalysisOutboxCoordinator;
import com.danycb.findocAnalyzer.features.vault.application.event.AnalysisOutboxEnqueuedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AnalysisOutboxScheduler {
    private final AnalysisOutboxCoordinator coordinator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEnqueued(AnalysisOutboxEnqueuedEvent ignored) {
        coordinator.requestDrain();
    }

    @Scheduled(fixedDelayString = "${findoc.analysis-outbox.poll-interval:PT1M}")
    public void recoverMissedWakeups() {
        coordinator.requestDrain();
    }
}
