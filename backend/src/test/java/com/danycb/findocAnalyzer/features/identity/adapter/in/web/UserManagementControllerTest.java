package com.danycb.findocAnalyzer.features.identity.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.dto.ChangeRoleCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.CreateUserCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.in.ChangeUserRoleUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.CreateUserUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.DeleteUserUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.ListUsersUseCase;
import com.danycb.findocAnalyzer.features.identity.domain.User;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for {@link UserManagementController}: verifies user-management wiring, that the
 * caller's principal is adapted to an {@link AuthenticatedUser} for every use case, request
 * validation, service-level forbidden operations mapping to 403, and that
 * {@code @RequireAdminOrSuperAdmin} method security is enforced. Collaborators are recording fakes.
 */
@WebMvcTest(UserManagementController.class)
@Import(UserManagementControllerTest.TestConfig.class)
class UserManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecordingCreateUser createUser;
    @Autowired
    private RecordingListUsers listUsers;
    @Autowired
    private RecordingDeleteUser deleteUser;
    @Autowired
    private RecordingChangeRole changeRole;

    private final UUID callerId = UUID.randomUUID();
    private final UUID callerTeamId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        createUser.reset();
        listUsers.reset();
        deleteUser.reset();
        changeRole.reset();
    }

    private RequestPostProcessor admin() {
        UserPrincipal user = new UserPrincipal("caller", callerId, UserRole.ADMIN, callerTeamId);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static RequestPostProcessor principal(UserRole role, String authority) {
        UserPrincipal user = new UserPrincipal("caller", UUID.randomUUID(), role, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    private User user(String username, UserRole role) {
        return new User(UUID.randomUUID(), username, "hash", role, callerTeamId);
    }

    @Test
    void createReturns201AndAdaptsPrincipal() throws Exception {
        createUser.result = user("bob", UserRole.MEMBER);

        mockMvc.perform(post("/api/v1/users")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"password123\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"));

        assertThat(createUser.caller.userId()).isEqualTo(callerId);
        assertThat(createUser.caller.role()).isEqualTo(UserRole.ADMIN);
        assertThat(createUser.caller.teamId()).isEqualTo(callerTeamId);
        assertThat(createUser.command.username()).isEqualTo("bob");
    }

    @Test
    void listReturnsUsers() throws Exception {
        listUsers.result = List.of(user("bob", UserRole.MEMBER), user("carol", UserRole.MEMBER));

        mockMvc.perform(get("/api/v1/users").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bob"))
                .andExpect(jsonPath("$[1].username").value("carol"));

        assertThat(listUsers.caller.userId()).isEqualTo(callerId);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/" + id).with(admin()))
                .andExpect(status().isNoContent());

        assertThat(deleteUser.id).isEqualTo(id);
        assertThat(deleteUser.caller.userId()).isEqualTo(callerId);
    }

    @Test
    void changeRoleReturnsUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        changeRole.result = user("bob", UserRole.ADMIN);

        mockMvc.perform(patch("/api/v1/users/" + id + "/role")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(changeRole.id).isEqualTo(id);
        assertThat(changeRole.command.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"short\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forbiddenServiceOperationMapsTo403() throws Exception {
        createUser.error = new ForbiddenOperationException("An admin cannot create another admin");

        mockMvc.perform(post("/api/v1/users")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(principal(UserRole.MEMBER, "ROLE_MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    // ---- fakes ------------------------------------------------------------------------------

    static class RecordingCreateUser implements CreateUserUseCase {
        AuthenticatedUser caller;
        CreateUserCommand command;
        User result;
        RuntimeException error;

        void reset() {
            caller = null;
            command = null;
            result = null;
            error = null;
        }

        @Override
        public User create(AuthenticatedUser caller, CreateUserCommand command) {
            this.caller = caller;
            this.command = command;
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    static class RecordingListUsers implements ListUsersUseCase {
        AuthenticatedUser caller;
        List<User> result = List.of();

        void reset() {
            caller = null;
            result = List.of();
        }

        @Override
        public List<User> list(AuthenticatedUser caller) {
            this.caller = caller;
            return result;
        }
    }

    static class RecordingDeleteUser implements DeleteUserUseCase {
        AuthenticatedUser caller;
        UUID id;

        void reset() {
            caller = null;
            id = null;
        }

        @Override
        public void delete(AuthenticatedUser caller, UUID userId) {
            this.caller = caller;
            this.id = userId;
        }
    }

    static class RecordingChangeRole implements ChangeUserRoleUseCase {
        AuthenticatedUser caller;
        UUID id;
        ChangeRoleCommand command;
        User result;

        void reset() {
            caller = null;
            id = null;
            command = null;
            result = null;
        }

        @Override
        public User changeRole(AuthenticatedUser caller, UUID userId, ChangeRoleCommand command) {
            this.caller = caller;
            this.id = userId;
            this.command = command;
            return result;
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
        RecordingCreateUser createUser() {
            return new RecordingCreateUser();
        }

        @Bean
        RecordingListUsers listUsers() {
            return new RecordingListUsers();
        }

        @Bean
        RecordingDeleteUser deleteUser() {
            return new RecordingDeleteUser();
        }

        @Bean
        RecordingChangeRole changeRole() {
            return new RecordingChangeRole();
        }
    }
}
