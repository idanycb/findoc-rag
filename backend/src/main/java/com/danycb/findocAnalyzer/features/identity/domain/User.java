package com.danycb.findocAnalyzer.features.identity.domain;

import java.util.UUID;

/**
 * A platform user. {@code teamId} is {@code null} for a {@link UserRole#SUPER_ADMIN},
 * who is global and not bound to any team; {@code ADMIN} and {@code MEMBER} always belong
 * to exactly one team.
 */
public record User(UUID id, String username, String passwordHash, UserRole role, UUID teamId) {
}
