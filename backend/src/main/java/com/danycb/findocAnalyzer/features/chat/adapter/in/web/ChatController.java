package com.danycb.findocAnalyzer.features.chat.adapter.in.web;

import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {
    private final AnswerQuestionUseCase answerQuestion;

    @PostMapping
    public ChatResponse ask(@RequestBody @Valid ChatRequest request) {
        String answer = answerQuestion.execute(request.question());
        return new ChatResponse(answer);
    }
}
