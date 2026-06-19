package com.danycb.findocAnalyzer.features.identity.adapter.out.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessTokenAdapterTest {

    private static final String SECRET = "this-is-a-test-secret-that-is-at-least-32-bytes-long";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    private final JwtAccessTokenAdapter adapter = new JwtAccessTokenAdapter(SECRET);

    @Test
    void generate_includesCorrectClaims() {
        UUID userId = UUID.randomUUID();

        String token = adapter.generate(userId, "alice");

        Claims claims = Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
    }

    @Test
    void generate_setsExpirationInTheFuture() {
        String token = adapter.generate(UUID.randomUUID(), "alice");

        Claims claims = Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getExpiration()).isAfter(new Date());
    }
}
