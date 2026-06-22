package com.danycb.findocAnalyzer.features.identity.adapter.out.security;

import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
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

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void generate_includesRoleAndTeamClaims() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        User user = new User(userId, "alice", "hash", UserRole.ADMIN, teamId);

        Claims claims = parse(adapter.generate(user));

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("teamId", String.class)).isEqualTo(teamId.toString());
    }

    @Test
    void generate_omitsTeamClaimForTeamlessSuperAdmin() {
        User superAdmin = new User(UUID.randomUUID(), "root", "hash", UserRole.SUPER_ADMIN, null);

        Claims claims = parse(adapter.generate(superAdmin));

        assertThat(claims.get("role", String.class)).isEqualTo("SUPER_ADMIN");
        assertThat(claims.get("teamId", String.class)).isNull();
    }

    @Test
    void generate_setsExpirationInTheFuture() {
        User user = new User(UUID.randomUUID(), "alice", "hash", UserRole.MEMBER, UUID.randomUUID());

        Claims claims = parse(adapter.generate(user));

        assertThat(claims.getExpiration()).isAfter(new Date());
    }
}
