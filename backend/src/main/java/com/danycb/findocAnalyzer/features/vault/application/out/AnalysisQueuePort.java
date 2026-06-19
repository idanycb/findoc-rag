package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;

public interface AnalysisQueuePort {
    void enqueue(DocumentAnalysisMessage message);
}
