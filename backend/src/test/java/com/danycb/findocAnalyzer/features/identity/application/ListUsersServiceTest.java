package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryUserRepository;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListUsersServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final ListUsersService service = new ListUsersService(users);

    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    private AuthenticatedUser superAdmin() {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN, null);
    }

    private AuthenticatedUser adminOf(UUID teamId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.ADMIN, teamId);
    }

    @Test
    void superAdmin_seesUsersAcrossAllTeams() {
        users.seed(new User(UUID.randomUUID(), "alice", "h", UserRole.ADMIN, teamA));
        users.seed(new User(UUID.randomUUID(), "bob", "h", UserRole.MEMBER, teamB));

        var result = service.list(superAdmin());

        assertThat(result).extracting(User::username).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void nonSuperAdmin_seesOnlyOwnTeamUsers() {
        users.seed(new User(UUID.randomUUID(), "alice", "h", UserRole.ADMIN, teamA));
        users.seed(new User(UUID.randomUUID(), "bob", "h", UserRole.MEMBER, teamB));

        var result = service.list(adminOf(teamA));

        assertThat(result).extracting(User::username).containsExactly("alice");
    }
}
