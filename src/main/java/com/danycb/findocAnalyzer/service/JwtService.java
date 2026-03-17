package com.danycb.findocAnalyzer.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${JWT_SECRET}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24h
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        Claims payload = extractPayload(token);

        if (!isExpired(payload)) {
            return payload.getSubject();
        } else {
            return null;
        }
    }

    private boolean isExpired(Claims payload) {
        Date expiration = payload.getExpiration();
        return expiration.before(new Date());
    }

    private Claims extractPayload(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
