package com.danycb.findocAnalyzer.features.identity.domain;

import java.util.UUID;

public record User(UUID id, String username, String passwordHash, UserRole role) {
}
