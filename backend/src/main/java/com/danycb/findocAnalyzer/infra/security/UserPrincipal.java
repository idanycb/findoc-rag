package com.danycb.findocAnalyzer.infra.security;

import java.util.UUID;

public record UserPrincipal(String username, UUID userId) {
}
