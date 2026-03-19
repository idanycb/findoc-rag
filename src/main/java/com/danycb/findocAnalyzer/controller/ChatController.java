package com.danycb.findocAnalyzer.controller;

import com.danycb.findocAnalyzer.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<Map<String, String>> ask(@RequestBody Map<String, String> payload, @AuthenticationPrincipal String username) {
        String question = payload.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Questions is required"));
        }

        String answer = chatService.answerQuestion(question, username);
        return ResponseEntity.ok(Map.of("answer", answer));
    }
}
