package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.OnboardCommand;
import com.danycb.findocAnalyzer.features.identity.domain.User;

public interface OnboardSuperAdminUseCase {
    User onboard(OnboardCommand command);

    boolean isOnboardingEnabled();
}
