package com.danycb.findocAnalyzer.features.chat.application.out;

public interface LlmPort {
    String answerWithContext(String context, String question);
}
