package com.danycb.findocAnalyzer.features.chat.adapter.in.web;

import com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto.ChatRequest;
import com.danycb.findocAnalyzer.features.chat.application.dto.AnswerResult;
import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("eval")
@RequestMapping("/api/v1/eval/chat")
@RequiredArgsConstructor
public class EvalChatController {
    private final AnswerQuestionUseCase answerQuestion;

    @PostMapping
    public AnswerResult ask(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid ChatRequest request) {
        if (principal.teamId() == null) {
            throw new AccessDeniedException("This account is not a member of a team");
        }
        return answerQuestion.execute(request.question(), principal.teamId());
    }
}
