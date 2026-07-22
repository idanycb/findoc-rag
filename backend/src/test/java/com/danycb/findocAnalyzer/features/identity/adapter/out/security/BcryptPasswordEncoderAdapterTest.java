package com.danycb.findocAnalyzer.features.identity.adapter.out.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordEncoderAdapterTest {

    private final BcryptPasswordEncoderAdapter adapter =
            new BcryptPasswordEncoderAdapter(new BCryptPasswordEncoder(12));

    @Test
    void hash_returnsAHashDifferentFromTheRawPassword() {
        String hash = adapter.hash("password123");

        assertThat(hash).isNotEqualTo("password123");
        assertThat(hash).isNotBlank();
    }

    @Test
    void matches_returnsTrueForTheCorrectPassword() {
        String hash = adapter.hash("password123");

        assertThat(adapter.matches("password123", hash)).isTrue();
    }

    @Test
    void matches_returnsFalseForAWrongPassword() {
        String hash = adapter.hash("password123");

        assertThat(adapter.matches("wrong-password", hash)).isFalse();
    }
}
