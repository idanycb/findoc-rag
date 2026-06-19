package com.danycb.findocAnalyzer.features.chat.application.out;

import java.util.List;

public interface LlmPort {
    List<String> generateSearchVariations(String question);

    String selectRelevantIndices(String question, String chunkList);

    String answerWithContext(String context, String question);
}
