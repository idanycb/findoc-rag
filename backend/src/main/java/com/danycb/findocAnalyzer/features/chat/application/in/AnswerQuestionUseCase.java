package com.danycb.findocAnalyzer.features.chat.application.in;

import java.util.UUID;

public interface AnswerQuestionUseCase {
    String execute(String question, UUID teamId);
}
