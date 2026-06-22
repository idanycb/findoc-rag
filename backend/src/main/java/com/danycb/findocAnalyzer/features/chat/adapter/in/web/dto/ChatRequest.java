package com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Question is required")
        @Size(max = 1000, message = "Question must be under 1000 characters")
        String question) {
}
