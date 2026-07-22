package com.danycb.findocAnalyzer.features.identity.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.application.dto.OnboardCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.OnboardingDisabledException;
import com.danycb.findocAnalyzer.features.identity.application.in.OnboardSuperAdminUseCase;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for {@link OnboardingController}: the public one-time super-admin bootstrap.
 * Verifies the onboarding-status flag, successful creation, request validation, and that a disabled
 * onboarding attempt is translated to 409. A recording fake stands in for the use case.
 */
@WebMvcTest(OnboardingController.class)
@Import(OnboardingControllerTest.TestConfig.class)
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecordingOnboard onboard;

    @BeforeEach
    void reset() {
        onboard.reset();
    }

    @Test
    void statusReflectsOnboardingEnabledFlag() throws Exception {
        onboard.enabled = true;

        mockMvc.perform(get("/api/v1/onboarding/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void onboardCreatesSuperAdmin() throws Exception {
        onboard.result = new User(UUID.randomUUID(), "root", "hash", UserRole.SUPER_ADMIN, null);

        mockMvc.perform(post("/api/v1/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"root\",\"password\":\"supersecret\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("root"))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));

        assertThat(onboard.command.username()).isEqualTo("root");
    }

    @Test
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"root\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns409WhenOnboardingDisabled() throws Exception {
        onboard.error = new OnboardingDisabledException("Onboarding is already complete");

        mockMvc.perform(post("/api/v1/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"root\",\"password\":\"supersecret\"}"))
                .andExpect(status().isConflict());
    }

    // ---- fake -------------------------------------------------------------------------------

    static class RecordingOnboard implements OnboardSuperAdminUseCase {
        boolean enabled;
        OnboardCommand command;
        User result;
        RuntimeException error;

        void reset() {
            enabled = false;
            command = null;
            result = null;
            error = null;
        }

        @Override
        public User onboard(OnboardCommand command) {
            this.command = command;
            if (error != null) {
                throw error;
            }
            return result;
        }

        @Override
        public boolean isOnboardingEnabled() {
            return enabled;
        }
    }

    @TestConfiguration
    @EnableWebSecurity
    static class TestConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        @Bean
        RecordingOnboard onboard() {
            return new RecordingOnboard();
        }
    }
}
