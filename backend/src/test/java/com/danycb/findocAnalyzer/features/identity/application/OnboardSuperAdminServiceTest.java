package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.infra.config.NoOpDeploymentLimits;
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

    private final CountingUserRepository users = new CountingUserRepository();
    private final RecordingOnboardingLock onboardingLock = new RecordingOnboardingLock();
    private final OnboardSuperAdminService service =
            new OnboardSuperAdminService(
                    users,
                    users,
                    new PlaintextPasswordEncoder(),
                    new NoOpAuditLogger(),
                    new NoOpDeploymentLimits(),
                    onboardingLock);

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
        assertThat(onboardingLock.acquisitions).isEqualTo(1);
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

    @Test
    void isOnboardingEnabled_reusesTheCompletedDatabaseResult() {
        users.seed(new User(UUID.randomUUID(), "existing", "h", UserRole.MEMBER, UUID.randomUUID()));

        assertThat(service.isOnboardingEnabled()).isFalse();
        assertThat(service.isOnboardingEnabled()).isFalse();

        assertThat(users.existsAnyCalls).isEqualTo(1);
    }

    @Test
    void isOnboardingEnabled_doesNotCacheEnabledAcrossReplicas() {
        assertThat(service.isOnboardingEnabled()).isTrue();
        assertThat(service.isOnboardingEnabled()).isTrue();

        assertThat(users.existsAnyCalls).isEqualTo(2);
    }

    @Test
    void onboard_marksOnboardingCompleteWithoutAnotherDatabaseCheck() {
        service.onboard(new OnboardCommand("root", "password123"));

        assertThat(service.isOnboardingEnabled()).isFalse();
        assertThat(users.existsAnyCalls).isEqualTo(1);
        assertThat(onboardingLock.acquisitions).isEqualTo(1);
    }

    private static final class CountingUserRepository extends InMemoryUserRepository {
        private int existsAnyCalls;

        @Override
        public boolean existsAny() {
            existsAnyCalls++;
            return super.existsAny();
        }
    }

    private static final class RecordingOnboardingLock
            implements com.danycb.findocAnalyzer.features.identity.application.out.OnboardingLockPort {
        private int acquisitions;

        @Override
        public void acquire() {
            acquisitions++;
        }
    }
}
