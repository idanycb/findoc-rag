package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.infra.config.DeploymentLimitsEnforcer;
import com.danycb.findocAnalyzer.infra.config.FindocLimitsProperties;
import com.danycb.findocAnalyzer.infra.config.NoOpDeploymentLimits;
import com.danycb.findocAnalyzer.infra.exception.LimitExceededException;
import com.danycb.findocAnalyzer.features.identity.application.dto.CreateUserCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.DuplicateUsernameException;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.exception.NotFoundException;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryTeamRepository;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryUserRepository;
import com.danycb.findocAnalyzer.features.identity.application.fakes.NoOpAuditLogger;
import com.danycb.findocAnalyzer.features.identity.application.fakes.PlaintextPasswordEncoder;
import com.danycb.findocAnalyzer.features.identity.domain.Team;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateUserServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final CreateUserService service =
            new CreateUserService(users, users, teams, new PlaintextPasswordEncoder(), new NoOpAuditLogger(), new NoOpDeploymentLimits());

    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    private AuthenticatedUser superAdmin() {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN, null);
    }

    private AuthenticatedUser adminOf(UUID teamId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.ADMIN, teamId);
    }

    @Test
    void superAdmin_canCreateAdminInAnyTeam() {
        teams.seed(new Team(teamA, "Team A", null));

        User created = service.create(superAdmin(),
                new CreateUserCommand("alice", "password123", UserRole.ADMIN, teamA));

        assertThat(created.role()).isEqualTo(UserRole.ADMIN);
        assertThat(created.teamId()).isEqualTo(teamA);
    }

    @Test
    void superAdmin_cannotCreateAnotherSuperAdmin() {
        teams.seed(new Team(teamA, "Team A", null));

        assertThatThrownBy(() -> service.create(superAdmin(),
                new CreateUserCommand("x", "password123", UserRole.SUPER_ADMIN, teamA)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void superAdmin_requiresTeamId() {
        assertThatThrownBy(() -> service.create(superAdmin(),
                new CreateUserCommand("x", "password123", UserRole.MEMBER, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void superAdmin_rejectsUnknownTeam() {
        assertThatThrownBy(() -> service.create(superAdmin(),
                new CreateUserCommand("x", "password123", UserRole.MEMBER, UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void admin_alwaysCreatesMemberInOwnTeam_ignoringRequestedRoleAndTeam() {
        User created = service.create(adminOf(teamA),
                new CreateUserCommand("bob", "password123", UserRole.ADMIN, teamB));

        assertThat(created.role()).isEqualTo(UserRole.MEMBER);
        assertThat(created.teamId()).isEqualTo(teamA);
    }

    @Test
    void duplicateUsername_isRejected() {
        users.seed(new User(UUID.randomUUID(), "taken", "h", UserRole.MEMBER, teamA));

        assertThatThrownBy(() -> service.create(adminOf(teamA),
                new CreateUserCommand("taken", "password123", null, null)))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void atUserLimit_isRejected() {
        FindocLimitsProperties properties = new FindocLimitsProperties();
        properties.setEnabled(true);
        properties.setMaxUsers(10);
        CreateUserService limitedService = new CreateUserService(
                users, users, teams, new PlaintextPasswordEncoder(), new NoOpAuditLogger(),
                new DeploymentLimitsEnforcer(properties));

        for (int i = 0; i < 10; i++) {
            users.seed(new User(UUID.randomUUID(), "user" + i, "h", UserRole.MEMBER, teamA));
        }

        assertThatThrownBy(() -> limitedService.create(adminOf(teamA),
                new CreateUserCommand("one-too-many", "password123", null, null)))
                .isInstanceOf(LimitExceededException.class);
    }
}
