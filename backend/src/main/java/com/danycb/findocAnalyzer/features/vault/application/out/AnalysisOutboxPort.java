package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalysisOutboxPort {
    void enqueue(DocumentAnalysisMessage message);

    List<ClaimedAnalysisRequest> claimDue(Instant now, int limit, Duration leaseDuration);

    void markPublished(UUID outboxId, UUID claimToken, Instant publishedAt);

    void markFailed(UUID outboxId, UUID claimToken, Instant nextAttemptAt, String error);

    record ClaimedAnalysisRequest(
            UUID outboxId,
            UUID claimToken,
            DocumentAnalysisMessage message,
            int attemptCount) {
    }
}
