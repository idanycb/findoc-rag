package com.danycb.findocAnalyzer.features.identity.application.dto;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code role} and {@code teamId} are honored only when a SUPER_ADMIN is the caller; an ADMIN
 * always creates a MEMBER in their own team regardless of what is sent.
 */
public record CreateUserCommand(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters") String password,
        UserRole role,
        UUID teamId) {
}
