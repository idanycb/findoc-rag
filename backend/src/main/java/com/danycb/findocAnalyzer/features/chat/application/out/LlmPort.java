package com.danycb.findocAnalyzer.features.chat.application.out;

public interface LlmPort {
    String rewriteForSearch(String question);
    String generateHypotheticalAnswer(String question);
    String answerWithContext(String context, String question);
}
