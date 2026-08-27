package com.danycb.findocAnalyzer.features.identity.application.out;

/** Serializes the one-time onboarding decision across application replicas. */
public interface OnboardingLockPort {
    void acquire();
}
