package com.danycb.findocAnalyzer.features.identity.adapter.out.security;

import com.danycb.findocAnalyzer.features.identity.application.out.PasswordVerifierPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BcryptPasswordVerifierAdapter implements PasswordVerifierPort {
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
