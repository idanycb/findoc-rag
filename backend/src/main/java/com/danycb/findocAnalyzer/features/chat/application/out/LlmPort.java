package com.danycb.findocAnalyzer.features.chat.application.out;

import com.danycb.findocAnalyzer.features.chat.domain.GroundedAnswer;

public interface LlmPort {
    String rewriteForSearch(String question);
    String generateHypotheticalAnswer(String question);
    GroundedAnswer answerWithContext(String context, String question);
}
