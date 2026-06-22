package com.danycb.findocAnalyzer.features.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record Team(UUID id, String name, Instant createdAt) {
}
