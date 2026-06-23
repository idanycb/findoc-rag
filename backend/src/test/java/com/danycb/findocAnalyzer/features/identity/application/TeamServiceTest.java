package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.infra.config.DeploymentLimitsEnforcer;
import com.danycb.findocAnalyzer.infra.config.FindocLimitsProperties;
import com.danycb.findocAnalyzer.infra.config.NoOpDeploymentLimits;
import com.danycb.findocAnalyzer.infra.exception.LimitExceededException;
import com.danycb.findocAnalyzer.features.identity.application.dto.TeamCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.DuplicateTeamNameException;
import com.danycb.findocAnalyzer.features.identity.application.exception.NotFoundException;
import com.danycb.findocAnalyzer.features.identity.application.exception.TeamNotEmptyException;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryTeamRepository;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryUserRepository;
import com.danycb.findocAnalyzer.features.identity.domain.Team;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamServiceTest {

    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final TeamService service = new TeamService(teams, users, new NoOpDeploymentLimits());

    @Nested
    class Create {
        @Test
        void creatingATeam_succeeds() {
            Team team = service.create(new TeamCommand("Engineering"));

            assertThat(team.name()).isEqualTo("Engineering");
            assertThat(team.id()).isNotNull();
            assertThat(teams.findById(team.id())).isPresent();
        }

        @Test
        void creatingATeamWithDuplicateName_isRejected() {
            teams.seed(new Team(UUID.randomUUID(), "Engineering", null));

            assertThatThrownBy(() -> service.create(new TeamCommand("Engineering")))
                    .isInstanceOf(DuplicateTeamNameException.class);
        }

        @Test
        void atTeamLimit_isRejected() {
            FindocLimitsProperties properties = new FindocLimitsProperties();
            properties.setEnabled(true);
            properties.setMaxTeams(3);
            TeamService limitedService = new TeamService(teams, users, new DeploymentLimitsEnforcer(properties));

            teams.seed(new Team(UUID.randomUUID(), "One", null));
            teams.seed(new Team(UUID.randomUUID(), "Two", null));
            teams.seed(new Team(UUID.randomUUID(), "Three", null));

            assertThatThrownBy(() -> limitedService.create(new TeamCommand("Four")))
                    .isInstanceOf(LimitExceededException.class);
        }
    }

    @Nested
    class ListAll {
        @Test
        void listingTeams_returnsAll() {
            teams.seed(new Team(UUID.randomUUID(), "Alpha", null));
            teams.seed(new Team(UUID.randomUUID(), "Beta", null));

            List<Team> result = service.list();

            assertThat(result).extracting(Team::name).containsExactly("Alpha", "Beta");
        }
    }

    @Nested
    class Update {
        @Test
        void updatingATeamName_succeeds() {
            Team team = teams.seed(new Team(UUID.randomUUID(), "Old", null));

            Team updated = service.update(team.id(), new TeamCommand("New"));

            assertThat(updated.name()).isEqualTo("New");
            assertThat(updated.id()).isEqualTo(team.id());
        }

        @Test
        void updatingWithSameName_succeeds() {
            Team team = teams.seed(new Team(UUID.randomUUID(), "Same", null));

            Team updated = service.update(team.id(), new TeamCommand("Same"));

            assertThat(updated.name()).isEqualTo("Same");
        }

        @Test
        void updatingToADuplicateName_isRejected() {
            Team team = teams.seed(new Team(UUID.randomUUID(), "Alpha", null));
            teams.seed(new Team(UUID.randomUUID(), "Beta", null));

            assertThatThrownBy(() -> service.update(team.id(), new TeamCommand("Beta")))
                    .isInstanceOf(DuplicateTeamNameException.class);
        }

        @Test
        void updatingAnUnknownTeam_isNotFound() {
            assertThatThrownBy(() -> service.update(UUID.randomUUID(), new TeamCommand("X")))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Delete {
        @Test
        void deletingAnEmptyTeam_succeeds() {
            Team team = teams.seed(new Team(UUID.randomUUID(), "Empty", null));

            service.delete(team.id());

            assertThat(teams.findById(team.id())).isEmpty();
        }

        @Test
        void deletingANonEmptyTeam_isRejected() {
            Team team = teams.seed(new Team(UUID.randomUUID(), "Busy", null));
            users.seed(new User(UUID.randomUUID(), "m", "h", UserRole.MEMBER, team.id()));

            assertThatThrownBy(() -> service.delete(team.id()))
                    .isInstanceOf(TeamNotEmptyException.class);
        }

        @Test
        void deletingAnUnknownTeam_isNotFound() {
            assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
