package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.OnboardCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.OnboardingDisabledException;
import com.danycb.findocAnalyzer.features.identity.application.fakes.InMemoryUserRepository;
import com.danycb.findocAnalyzer.features.identity.application.fakes.NoOpAuditLogger;
import com.danycb.findocAnalyzer.features.identity.application.fakes.PlaintextPasswordEncoder;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnboardSuperAdminServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final OnboardSuperAdminService service =
            new OnboardSuperAdminService(users, users, new PlaintextPasswordEncoder(), new NoOpAuditLogger());

    @Test
    void onboard_onEmptySystem_createsTeamlessSuperAdminWithHashedPassword() {
        User created = service.onboard(new OnboardCommand("root", "password123"));

        assertThat(created.role()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(created.teamId()).isNull();
        assertThat(created.id()).isNotNull();
        assertThat(users.findById(created.id())).get()
                .extracting(User::passwordHash)
                .isEqualTo("hashed:password123");
    }

    @Test
    void onboard_whenAUserAlreadyExists_isRejected() {
        users.seed(new User(UUID.randomUUID(), "existing", "h", UserRole.MEMBER, UUID.randomUUID()));

        assertThatThrownBy(() -> service.onboard(new OnboardCommand("root", "password123")))
                .isInstanceOf(OnboardingDisabledException.class);
    }

    @Test
    void isOnboardingEnabled_onEmptySystem_returnsTrue() {
        assertThat(service.isOnboardingEnabled()).isTrue();
    }

    @Test
    void isOnboardingEnabled_whenUsersExist_returnsFalse() {
        users.seed(new User(UUID.randomUUID(), "existing", "h", UserRole.MEMBER, UUID.randomUUID()));

        assertThat(service.isOnboardingEnabled()).isFalse();
    }
}
