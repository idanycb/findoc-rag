package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.dto.ChangeRoleCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryUserRepository;
import com.danycb.findocAnalyzer.features.identity.application.fakes.NoOpAuditLogger;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeUserRoleServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final ChangeUserRoleService service = new ChangeUserRoleService(users, users, new NoOpAuditLogger());

    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    private AuthenticatedUser superAdmin() {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN, null);
    }

    private AuthenticatedUser adminOf(UUID teamId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.ADMIN, teamId);
    }

    private User member(UUID teamId) {
        return users.seed(new User(UUID.randomUUID(), "m" + UUID.randomUUID(), "h", UserRole.MEMBER, teamId));
    }

    private User admin(UUID teamId) {
        return users.seed(new User(UUID.randomUUID(), "a" + UUID.randomUUID(), "h", UserRole.ADMIN, teamId));
    }

    @Test
    void admin_canPromoteMemberToAdminInOwnTeam() {
        User target = member(teamA);

        User updated = service.changeRole(adminOf(teamA), target.id(), new ChangeRoleCommand(UserRole.ADMIN));

        assertThat(updated.role()).isEqualTo(UserRole.ADMIN);
        assertThat(updated.teamId()).isEqualTo(teamA);
    }

    @Test
    void admin_cannotDemoteAdmin() {
        User target = admin(teamA);

        assertThatThrownBy(() -> service.changeRole(adminOf(teamA), target.id(),
                new ChangeRoleCommand(UserRole.MEMBER)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void admin_cannotPromoteAcrossTeams() {
        User target = member(teamB);

        assertThatThrownBy(() -> service.changeRole(adminOf(teamA), target.id(),
                new ChangeRoleCommand(UserRole.ADMIN)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void superAdmin_canDemoteAdminToMember() {
        User target = admin(teamA);

        User updated = service.changeRole(superAdmin(), target.id(), new ChangeRoleCommand(UserRole.MEMBER));

        assertThat(updated.role()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void changingASuperAdminRole_isRejected() {
        User target = users.seed(new User(UUID.randomUUID(), "root", "h", UserRole.SUPER_ADMIN, null));

        assertThatThrownBy(() -> service.changeRole(superAdmin(), target.id(),
                new ChangeRoleCommand(UserRole.MEMBER)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void promotingToSuperAdmin_isRejected() {
        User target = member(teamA);

        assertThatThrownBy(() -> service.changeRole(superAdmin(), target.id(),
                new ChangeRoleCommand(UserRole.SUPER_ADMIN)))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
