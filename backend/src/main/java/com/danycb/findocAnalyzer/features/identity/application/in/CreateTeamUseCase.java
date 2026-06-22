package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.TeamCommand;
import com.danycb.findocAnalyzer.features.identity.domain.Team;

public interface CreateTeamUseCase {
    Team create(TeamCommand command);
}
