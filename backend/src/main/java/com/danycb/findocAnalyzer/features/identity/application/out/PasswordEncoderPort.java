package com.danycb.findocAnalyzer.features.identity.application.out;

public interface PasswordEncoderPort {
    boolean matches(String rawPassword, String encodedPassword);

    String hash(String rawPassword);
}
