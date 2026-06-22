package com.danycb.findocAnalyzer.features.identity.application.exception;

/**
 * Thrown when onboarding is attempted but the system already has at least one user.
 */
public class OnboardingDisabledException extends RuntimeException {
    public OnboardingDisabledException(String message) {
        super(message);
    }
}
