package com.danycb.findocAnalyzer.features.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardCommand(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters") String password) {
}
