package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.TeamCommand;
import com.danycb.findocAnalyzer.features.identity.domain.Team;

import java.util.UUID;

public interface UpdateTeamUseCase {
    Team update(UUID teamId, TeamCommand command);
}
