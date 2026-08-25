package com.danycb.findocAnalyzer.features.chat.application.in;

import com.danycb.findocAnalyzer.features.chat.application.dto.AnswerResult;

import java.util.UUID;

public interface AnswerQuestionUseCase {
    AnswerResult execute(String question, UUID teamId);
}
