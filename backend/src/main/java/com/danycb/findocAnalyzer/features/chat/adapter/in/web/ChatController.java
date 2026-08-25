package com.danycb.findocAnalyzer.features.chat.adapter.in.web;

import com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto.ChatRequest;
import com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto.ChatResponse;
import com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto.CitationResponse;
import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {
    private final AnswerQuestionUseCase answerQuestion;

    @PostMapping
    public ChatResponse ask(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid ChatRequest request) {
        if (principal.teamId() == null) {
            throw new AccessDeniedException("This account is not a member of a team");
        }
        UUID teamId = principal.teamId();
        var result = answerQuestion.execute(request.question(), teamId);
        return new ChatResponse(result.answer(), result.citations().stream().map(CitationResponse::from).toList());
    }
}
