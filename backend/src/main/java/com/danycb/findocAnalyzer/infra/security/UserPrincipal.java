package com.danycb.findocAnalyzer.infra.security;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;

import java.util.UUID;

/**
 * Authenticated caller, derived from the JWT. {@code teamId} is {@code null} for a
 * {@link UserRole#SUPER_ADMIN} (teamless); set for ADMIN/MEMBER.
 */
public record UserPrincipal(String username, UUID userId, UserRole role, UUID teamId) {

    public static final class Fields {
        public static final String USERNAME = "username";
        public static final String USER_ID = "userId";
        public static final String ROLE = "role";
        public static final String TEAM_ID = "teamId";
    }
}
