package com.danycb.findocAnalyzer.infra.security;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final SecretKey key;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthFilter(
            @Value("${JWT_SECRET}") String secret,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();

            UserRole role = UserRole.valueOf(payload.get(UserPrincipal.Fields.ROLE, String.class));
            String teamIdClaim = payload.get(UserPrincipal.Fields.TEAM_ID, String.class);
            UUID teamId = teamIdClaim == null ? null : UUID.fromString(teamIdClaim);

            UserPrincipal principal = new UserPrincipal(
                    payload.getSubject(),
                    UUID.fromString(payload.get(UserPrincipal.Fields.USER_ID, String.class)),
                    role,
                    teamId
            );

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("event=invalid_bearer_token path={}", request.getRequestURI());
            handlerExceptionResolver.resolveException(
                    request, response, null, new InvalidAccessTokenException("Invalid or expired token"));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
