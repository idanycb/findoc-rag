package com.danycb.findocAnalyzer.features.chat.application.out;

import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;

import java.util.List;

public interface VectorSearchPort {
    List<RetrievedChunk> search(String query);
}
