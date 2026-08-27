package com.danycb.findocAnalyzer.features.vault.application.event;

import java.util.UUID;

public record AnalysisOutboxEnqueuedEvent(UUID outboxId) {
}
