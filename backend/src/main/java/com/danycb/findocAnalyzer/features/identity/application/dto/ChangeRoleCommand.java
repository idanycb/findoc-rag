package com.danycb.findocAnalyzer.features.identity.application.dto;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleCommand(@NotNull(message = "Role is required") UserRole role) {
}
