package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryUserRepository;
import com.danycb.findocAnalyzer.features.identity.application.fakes.NoOpAuditLogger;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeleteUserServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final DeleteUserService service = new DeleteUserService(users, users, new NoOpAuditLogger());

    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    private AuthenticatedUser superAdmin(UUID id) {
        return new AuthenticatedUser(id, UserRole.SUPER_ADMIN, null);
    }

    private AuthenticatedUser adminOf(UUID teamId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.ADMIN, teamId);
    }

    @Test
    void admin_canDeleteMemberInOwnTeam() {
        User member = users.seed(new User(UUID.randomUUID(), "m", "h", UserRole.MEMBER, teamA));

        service.delete(adminOf(teamA), member.id());

        assertThat(users.findById(member.id())).isEmpty();
    }

    @Test
    void admin_cannotDeleteAdmin() {
        User otherAdmin = users.seed(new User(UUID.randomUUID(), "a", "h", UserRole.ADMIN, teamA));

        assertThatThrownBy(() -> service.delete(adminOf(teamA), otherAdmin.id()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void admin_cannotDeleteMemberInAnotherTeam() {
        User member = users.seed(new User(UUID.randomUUID(), "m", "h", UserRole.MEMBER, teamB));

        assertThatThrownBy(() -> service.delete(adminOf(teamA), member.id()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void nobodyCanDeleteThemselves() {
        UUID id = UUID.randomUUID();
        users.seed(new User(id, "root", "h", UserRole.SUPER_ADMIN, null));

        assertThatThrownBy(() -> service.delete(superAdmin(id), id))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void superAdmin_canDeleteAnAdmin() {
        User admin = users.seed(new User(UUID.randomUUID(), "a", "h", UserRole.ADMIN, teamA));

        service.delete(superAdmin(UUID.randomUUID()), admin.id());

        assertThat(users.findById(admin.id())).isEmpty();
    }
}
