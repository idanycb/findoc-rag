package com.danycb.findocAnalyzer.features.identity.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.application.dto.LoginCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.LoginResult;
import com.danycb.findocAnalyzer.features.identity.application.exception.InvalidCredentialsException;
import com.danycb.findocAnalyzer.features.identity.application.in.LoginUseCase;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for {@link LoginController}: the public authentication endpoint. Verifies a
 * successful login returns the access token and that a rejected credential is translated to 401 by
 * {@link com.danycb.findocAnalyzer.infra.exception.GlobalExceptionHandler}. A recording fake stands
 * in for the use case.
 */
@WebMvcTest(LoginController.class)
@Import(LoginControllerTest.TestConfig.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecordingLogin login;

    @BeforeEach
    void reset() {
        login.reset();
    }

    @Test
    void returnsAccessTokenOnSuccess() throws Exception {
        login.result = new LoginResult("jwt-token-value");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token-value"));

        assertThat(login.command.username()).isEqualTo("alice");
        assertThat(login.command.password()).isEqualTo("secret123");
    }

    @Test
    void returns401OnInvalidCredentials() throws Exception {
        login.error = new InvalidCredentialsException("Invalid username or password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- fake -------------------------------------------------------------------------------

    static class RecordingLogin implements LoginUseCase {
        LoginCommand command;
        LoginResult result;
        RuntimeException error;

        void reset() {
            command = null;
            result = null;
            error = null;
        }

        @Override
        public LoginResult login(LoginCommand command) {
            this.command = command;
            if (error != null) {
                throw error;
            }
            return result;
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
        RecordingLogin login() {
            return new RecordingLogin();
        }
    }
}
