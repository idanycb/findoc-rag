package com.danycb.findocAnalyzer.features.vault.application.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRequestReceiptPort {
    Optional<ProcessingClaim> claim(
            UUID requestId, UUID documentId, Instant now, Duration leaseDuration);

    void markProcessed(UUID requestId, UUID claimToken, Instant processedAt);

    void release(UUID requestId, UUID claimToken, String error);

    record ProcessingClaim(UUID requestId, UUID claimToken) {
    }
}
