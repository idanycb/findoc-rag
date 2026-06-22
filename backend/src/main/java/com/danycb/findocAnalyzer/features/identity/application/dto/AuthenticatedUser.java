package com.danycb.findocAnalyzer.features.identity.application.dto;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;

import java.util.UUID;

/**
 * The authenticated caller performing a management operation, as seen by the application layer.
 * Decouples use cases from the web/security adapter's principal type.
 */
public record AuthenticatedUser(UUID userId, UserRole role, UUID teamId) {
}
