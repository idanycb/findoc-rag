package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.infra.config.DeploymentLimitsPort;
import com.danycb.findocAnalyzer.features.identity.application.dto.OnboardCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.OnboardingDisabledException;
import com.danycb.findocAnalyzer.features.identity.application.in.OnboardSuperAdminUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.OnboardingLockPort;
import com.danycb.findocAnalyzer.features.identity.application.out.PasswordEncoderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserWriterPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardSuperAdminService implements OnboardSuperAdminUseCase {
    private final UserWriterPort userWriter;
    private final UserReaderPort userExistence;
    private final PasswordEncoderPort passwordEncoder;
    private final IdentityAuditLogger audit;
    private final DeploymentLimitsPort limits;
    private final OnboardingLockPort onboardingLock;
    private volatile boolean onboardingKnownComplete;

    @Override
    public boolean isOnboardingEnabled() {
        if (onboardingKnownComplete) {
            return false;
        }

        boolean enabled = !userExistence.existsAny();
        if (!enabled) {
            onboardingKnownComplete = true;
        }
        return enabled;
    }

    @Override
    @Transactional
    public User onboard(OnboardCommand command) {
        onboardingLock.acquire();
        if (!isOnboardingEnabled()) {
            throw new OnboardingDisabledException(
                    "System already initialized; onboarding is disabled");
        }

        limits.assertCanAddUser(userExistence::countAll);

        User superAdmin = new User(
                null,
                command.username(),
                passwordEncoder.hash(command.password()),
                UserRole.SUPER_ADMIN,
                null);

        User saved = userWriter.save(superAdmin);
        audit.superAdminOnboarded(saved);
        onboardingKnownComplete = true;
        return saved;
    }
}
