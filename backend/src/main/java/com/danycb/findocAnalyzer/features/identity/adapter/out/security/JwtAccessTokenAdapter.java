package com.danycb.findocAnalyzer.features.identity.adapter.out.security;

import com.danycb.findocAnalyzer.features.identity.application.out.AccessTokenPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtAccessTokenAdapter implements AccessTokenPort {
    private static final class JwtClaims {
        static final String USER_ID = "userId";
        static final String ROLE = "role";
        static final String TEAM_ID = "teamId";
    }

    private final SecretKey key;

    public JwtAccessTokenAdapter(@Value("${JWT_SECRET}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public String generate(User user) {
        JwtBuilder builder = Jwts.builder()
                .subject(user.username())
                .claim(JwtClaims.USER_ID, user.id().toString())
                .claim(JwtClaims.ROLE, user.role().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 30))
                .signWith(key);

        if (user.teamId() != null) {
            builder.claim(JwtClaims.TEAM_ID, user.teamId().toString());
        }

        return builder.compact();
    }
}
