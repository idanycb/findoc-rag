package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.infra.config.DeploymentLimitsPort;
import com.danycb.findocAnalyzer.features.identity.application.dto.TeamCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.DuplicateTeamNameException;
import com.danycb.findocAnalyzer.features.identity.application.exception.NotFoundException;
import com.danycb.findocAnalyzer.features.identity.application.exception.TeamNotEmptyException;
import com.danycb.findocAnalyzer.features.identity.application.in.CreateTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.DeleteTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.ListTeamsUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.UpdateTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.TeamPersistencePort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.domain.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService implements CreateTeamUseCase, ListTeamsUseCase, UpdateTeamUseCase, DeleteTeamUseCase {
    private final TeamPersistencePort teams;
    private final UserReaderPort users;
    private final DeploymentLimitsPort limits;

    @Override
    @Transactional
    public Team create(TeamCommand command) {
        if (teams.existsByName(command.name())) {
            throw new DuplicateTeamNameException("Team: [" + command.name() + "] already exists");
        }
        limits.assertCanAddTeam(teams::countAll);
        Team saved = teams.save(new Team(null, command.name(), null));
        log.info("event=team_created teamId={} teamName={}", saved.id(), saved.name());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Team> list() {
        return teams.findAll();
    }

    @Override
    @Transactional
    public Team update(UUID teamId, TeamCommand command) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));

        if (team.name().equals(command.name())) {
            return team;
        }

        if (teams.existsByName(command.name())) {
            throw new DuplicateTeamNameException("Team: [" + command.name() + "] already exists");
        }

        Team saved = teams.save(new Team(team.id(), command.name(), team.createdAt()));
        log.info("event=team_renamed teamId={} teamName={}", saved.id(), saved.name());
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID teamId) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));
        if (users.countByTeamId(teamId) > 0) {
            throw new TeamNotEmptyException(
                    "Team: [" + teamId + "] still has members; remove or reassign them before deleting");
        }
        teams.deleteById(teamId);
        log.info("event=team_deleted teamId={} teamName={}", team.id(), team.name());
    }
}
