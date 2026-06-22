package com.danycb.findocAnalyzer.features.identity.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.identity.domain.Team;

import java.time.Instant;
import java.util.UUID;

public record TeamView(UUID id, String name, Instant createdAt) {
    public static TeamView from(Team team) {
        return new TeamView(team.id(), team.name(), team.createdAt());
    }
}
