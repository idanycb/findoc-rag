package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort.OutboxStatus;

public interface GetAnalysisOutboxStatusUseCase {
    OutboxStatus getStatus();
}
