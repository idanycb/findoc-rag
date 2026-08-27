package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.PublishAnalysisOutboxUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxPort;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisQueuePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublishAnalysisOutboxService implements PublishAnalysisOutboxUseCase {
    private static final int MAX_ERROR_LENGTH = 1000;

    private final AnalysisOutboxPort outbox;
    private final AnalysisQueuePort analysisQueue;

    @Override
    public int publishDue(
            Instant now,
            int limit,
            Duration leaseDuration,
            Duration retryDelay,
            Duration maxRetryDelay) {
        List<AnalysisOutboxPort.ClaimedAnalysisRequest> claimed = outbox.claimDue(now, limit, leaseDuration);
        for (AnalysisOutboxPort.ClaimedAnalysisRequest request : claimed) {
            try {
                analysisQueue.enqueue(request.message());
                outbox.markPublished(request.outboxId(), request.claimToken(), now);
            } catch (RuntimeException failure) {
                outbox.markFailed(
                        request.outboxId(),
                        request.claimToken(),
                        now.plus(retryDelay(request.attemptCount(), retryDelay, maxRetryDelay)),
                        conciseError(failure));
            }
        }
        return claimed.size();
    }

    private Duration retryDelay(int attemptCount, Duration initialDelay, Duration maximumDelay) {
        Duration delay = initialDelay;
        for (int attempt = 1; attempt < attemptCount && delay.compareTo(maximumDelay) < 0; attempt++) {
            if (delay.compareTo(maximumDelay.dividedBy(2)) > 0) {
                return maximumDelay;
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maximumDelay) > 0 ? maximumDelay : delay;
    }

    private String conciseError(RuntimeException failure) {
        String message = failure.getMessage();
        String error = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
        return error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
    }
}
