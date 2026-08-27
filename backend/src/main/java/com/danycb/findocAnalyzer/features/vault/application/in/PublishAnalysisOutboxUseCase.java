package com.danycb.findocAnalyzer.features.vault.application.in;

import java.time.Duration;
import java.time.Instant;

public interface PublishAnalysisOutboxUseCase {
    int publishDue(
            Instant now,
            int limit,
            Duration leaseDuration,
            Duration retryDelay,
            Duration maxRetryDelay);
}
