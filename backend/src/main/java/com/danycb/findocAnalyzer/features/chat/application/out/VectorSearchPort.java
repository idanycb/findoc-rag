package com.danycb.findocAnalyzer.features.chat.application.out;

import com.danycb.findocAnalyzer.features.chat.domain.RetrievalOutcome;

import java.util.UUID;

public interface VectorSearchPort {
    RetrievalOutcome search(String query, UUID teamId);
}
