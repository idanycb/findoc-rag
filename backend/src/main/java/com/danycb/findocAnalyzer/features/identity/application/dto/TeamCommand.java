package com.danycb.findocAnalyzer.features.identity.application.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamCommand(@NotBlank(message = "Team name is required") String name) {
}
