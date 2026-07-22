package com.danycb.findocAnalyzer.features.identity.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.application.dto.TeamCommand;
import com.danycb.findocAnalyzer.features.identity.application.in.CreateTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.DeleteTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.ListTeamsUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.UpdateTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.domain.Team;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for {@link TeamController}: verifies team CRUD wiring, request validation, and that
 * {@code @RequireSuperAdmin} method security restricts every endpoint to a super admin. Collaborators
 * are hand-written recording fakes.
 */
@WebMvcTest(TeamController.class)
@Import(TeamControllerTest.TestConfig.class)
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecordingCreateTeam createTeam;
    @Autowired
    private RecordingListTeams listTeams;
    @Autowired
    private RecordingUpdateTeam updateTeam;
    @Autowired
    private RecordingDeleteTeam deleteTeam;

    @BeforeEach
    void reset() {
        createTeam.reset();
        listTeams.reset();
        updateTeam.reset();
        deleteTeam.reset();
    }

    private static RequestPostProcessor principal(UserRole role, String authority) {
        UserPrincipal user = new UserPrincipal("caller", UUID.randomUUID(), role, null);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    private static RequestPostProcessor superAdmin() {
        return principal(UserRole.SUPER_ADMIN, "ROLE_SUPER_ADMIN");
    }

    private static Team team(String name) {
        return new Team(UUID.randomUUID(), name, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createReturns201() throws Exception {
        createTeam.result = team("Acme");

        mockMvc.perform(post("/api/v1/teams")
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme"));

        assertThat(createTeam.command.name()).isEqualTo("Acme");
    }

    @Test
    void listReturnsTeams() throws Exception {
        listTeams.result = List.of(team("Acme"), team("Globex"));

        mockMvc.perform(get("/api/v1/teams").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"))
                .andExpect(jsonPath("$[1].name").value("Globex"));
    }

    @Test
    void updateReturnsUpdatedTeam() throws Exception {
        UUID id = UUID.randomUUID();
        updateTeam.result = team("Renamed");

        mockMvc.perform(put("/api/v1/teams/" + id)
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));

        assertThat(updateTeam.id).isEqualTo(id);
        assertThat(updateTeam.command.name()).isEqualTo("Renamed");
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/teams/" + id).with(superAdmin()))
                .andExpect(status().isNoContent());

        assertThat(deleteTeam.id).isEqualTo(id);
    }

    @Test
    void rejectsBlankTeamName() throws Exception {
        mockMvc.perform(post("/api/v1/teams")
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/teams").with(principal(UserRole.ADMIN, "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/teams"))
                .andExpect(status().isForbidden());
    }

    // ---- fakes ------------------------------------------------------------------------------

    static class RecordingCreateTeam implements CreateTeamUseCase {
        TeamCommand command;
        Team result;

        void reset() {
            command = null;
            result = null;
        }

        @Override
        public Team create(TeamCommand command) {
            this.command = command;
            return result;
        }
    }

    static class RecordingListTeams implements ListTeamsUseCase {
        List<Team> result = List.of();

        void reset() {
            result = List.of();
        }

        @Override
        public List<Team> list() {
            return result;
        }
    }

    static class RecordingUpdateTeam implements UpdateTeamUseCase {
        UUID id;
        TeamCommand command;
        Team result;

        void reset() {
            id = null;
            command = null;
            result = null;
        }

        @Override
        public Team update(UUID teamId, TeamCommand command) {
            this.id = teamId;
            this.command = command;
            return result;
        }
    }

    static class RecordingDeleteTeam implements DeleteTeamUseCase {
        UUID id;

        void reset() {
            id = null;
        }

        @Override
        public void delete(UUID teamId) {
            this.id = teamId;
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
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        @Bean
        RecordingCreateTeam createTeam() {
            return new RecordingCreateTeam();
        }

        @Bean
        RecordingListTeams listTeams() {
            return new RecordingListTeams();
        }

        @Bean
        RecordingUpdateTeam updateTeam() {
            return new RecordingUpdateTeam();
        }

        @Bean
        RecordingDeleteTeam deleteTeam() {
            return new RecordingDeleteTeam();
        }
    }
}
