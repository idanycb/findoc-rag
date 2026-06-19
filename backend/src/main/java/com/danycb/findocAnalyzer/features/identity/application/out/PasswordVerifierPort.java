package com.danycb.findocAnalyzer.features.identity.application.out;

public interface PasswordVerifierPort {
    boolean matches(String rawPassword, String encodedPassword);
}
