package com.danycb.findocAnalyzer.features.chat.application.out;

import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;

import java.util.List;
import java.util.UUID;

public interface VectorSearchPort {
    List<RetrievedChunk> search(String query, UUID teamId);
}
