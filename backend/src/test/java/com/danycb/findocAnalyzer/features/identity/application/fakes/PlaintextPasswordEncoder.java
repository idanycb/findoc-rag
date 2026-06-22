package com.danycb.findocAnalyzer.features.identity.application.fakes;

import com.danycb.findocAnalyzer.features.identity.application.out.PasswordEncoderPort;

/** Test encoder that "hashes" by prefixing, so tests can assert hashing happened without BCrypt. */
public class PlaintextPasswordEncoder implements PasswordEncoderPort {
    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encodedPassword.equals(hash(rawPassword));
    }

    @Override
    public String hash(String rawPassword) {
        return "hashed:" + rawPassword;
    }
}
