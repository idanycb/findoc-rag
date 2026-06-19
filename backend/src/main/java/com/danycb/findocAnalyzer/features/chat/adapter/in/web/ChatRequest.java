package com.danycb.findocAnalyzer.features.chat.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank(message = "Question is required") String question) {
}
