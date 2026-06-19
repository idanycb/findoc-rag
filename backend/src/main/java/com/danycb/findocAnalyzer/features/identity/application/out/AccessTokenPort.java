package com.danycb.findocAnalyzer.features.identity.application.out;

import java.util.UUID;

public interface AccessTokenPort {
    String generate(UUID userId, String username);
}
