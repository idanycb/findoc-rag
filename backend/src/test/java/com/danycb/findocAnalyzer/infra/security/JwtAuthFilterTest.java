package com.danycb.findocAnalyzer.infra.security;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {
    private static final String SECRET = "this-is-a-test-secret-that-is-at-least-32-bytes-long";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_validToken_authenticatesRequest() throws ServletException, IOException {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        JwtAuthFilter filter = new JwtAuthFilter(SECRET, noOpResolver());
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        MockHttpServletRequest request = bearerRequest(token(userId, "alice", UserRole.ADMIN, teamId));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new UserPrincipal("alice", userId, UserRole.ADMIN, teamId));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void doFilterInternal_expiredToken_delegatesInvalidAccessTokenException() throws ServletException, IOException {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<Exception> resolvedException = new AtomicReference<>();
        JwtAuthFilter filter = new JwtAuthFilter(SECRET, capturingResolver(resolvedException));

        MockHttpServletRequest request = bearerRequest(expiredToken());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(resolvedException.get())
                .isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("Invalid or expired token");
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String token(UUID userId, String username, UserRole role, UUID teamId) {
        return Jwts.builder()
                .subject(username)
                .claim(UserPrincipal.Fields.USER_ID, userId.toString())
                .claim(UserPrincipal.Fields.ROLE, role.name())
                .claim(UserPrincipal.Fields.TEAM_ID, teamId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(KEY)
                .compact();
    }

    private String expiredToken() {
        return Jwts.builder()
                .subject("alice")
                .claim(UserPrincipal.Fields.USER_ID, UUID.randomUUID().toString())
                .claim(UserPrincipal.Fields.ROLE, UserRole.MEMBER.name())
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 30_000))
                .signWith(KEY)
                .compact();
    }

    private HandlerExceptionResolver noOpResolver() {
        return (request, response, handler, ex) -> null;
    }

    private HandlerExceptionResolver capturingResolver(AtomicReference<Exception> exception) {
        return (request, response, handler, ex) -> {
            exception.set(ex);
            return null;
        };
    }
}
