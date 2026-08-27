package com.danycb.findocAnalyzer.features.vault.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import com.danycb.findocAnalyzer.features.vault.application.in.GetAnalysisOutboxStatusUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort.OutboxStatus;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort.Stage;
import com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxMaintenancePort.StuckRequest;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisOutboxAdminController.class)
@Import(AnalysisOutboxAdminControllerTest.TestConfig.class)
class AnalysisOutboxAdminControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void superAdminCanInspectStuckRequestDetails() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analysis-outbox")
                        .with(authentication(UserAuthentication.forRole(UserRole.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stuckPublicationCount").value(1))
                .andExpect(jsonPath("$.stuckRequests[0].stage").value("PUBLICATION"))
                .andExpect(jsonPath("$.stuckRequests[0].lastError").value("SqsException: unavailable"));
    }

    @Test
    void adminCannotInspectTheGlobalOutbox() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analysis-outbox")
                        .with(authentication(UserAuthentication.forRole(UserRole.ADMIN))))
                .andExpect(status().isForbidden());
    }

    private static final class UserAuthentication {
        private static UsernamePasswordAuthenticationToken forRole(UserRole role) {
            UserPrincipal principal = new UserPrincipal(
                    "operator", UUID.randomUUID(), role, UUID.randomUUID());
            return new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        }
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        @Bean
        GetAnalysisOutboxStatusUseCase status() {
            Instant now = Instant.parse("2026-08-26T20:00:00Z");
            return () -> new OutboxStatus(
                    now, 2, 1, 1, 0, now.minusSeconds(3600), 4,
                    List.of(new StuckRequest(
                            UUID.randomUUID(), UUID.randomUUID(), Stage.PUBLICATION,
                            now.minusSeconds(3600), 4, now, "SqsException: unavailable")));
        }
    }
}
