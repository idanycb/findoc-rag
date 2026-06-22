package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.LoginCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.LoginResult;
import com.danycb.findocAnalyzer.features.identity.application.exception.InvalidCredentialsException;
import com.danycb.findocAnalyzer.features.identity.application.fakes.StubAccessToken;
import com.danycb.findocAnalyzer.features.identity.application.out.PasswordEncoderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginServiceTest {

    private final FakeUserLookup userLookup = new FakeUserLookup();
    private LoginService loginService;

    private final UUID userId = UUID.randomUUID();
    private final User alice = new User(userId, "alice", "hashed-pw", UserRole.MEMBER, UUID.randomUUID());

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userLookup, new PlaintextPasswordEncoder(), new StubAccessToken());
    }

    @Test
    void login_withValidCredentials_returnsToken() {
        userLookup.add(alice);

        LoginResult result = loginService.login(new LoginCommand("alice", "hashed-pw"));

        assertThat(result.accessToken()).isNotBlank();
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentials() {
        userLookup.add(alice);

        assertThatThrownBy(() -> loginService.login(new LoginCommand("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_withUnknownUser_throwsInvalidCredentials() {
        assertThatThrownBy(() -> loginService.login(new LoginCommand("nobody", "any")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    static class FakeUserLookup implements UserReaderPort {
        private final Map<String, User> users = new HashMap<>();

        void add(User user) {
            users.put(user.username(), user);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public boolean existsAny() {
            return !users.isEmpty();
        }

        @Override
        public Optional<User> findById(UUID id) {
            return users.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
        }

        @Override
        public List<User> findByTeamId(UUID teamId) {
            return users.values().stream().filter(u -> teamId.equals(u.teamId())).toList();
        }

        @Override
        public boolean existsByUsername(String username) {
            return users.containsKey(username);
        }

        @Override
        public long countByRole(UserRole role) {
            return users.values().stream().filter(u -> u.role() == role).count();
        }

        @Override
        public long countByTeamId(UUID teamId) {
            return users.values().stream().filter(u -> teamId.equals(u.teamId())).count();
        }
    }

    static class PlaintextPasswordEncoder implements PasswordEncoderPort {
        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return rawPassword.equals(encodedPassword);
        }

        @Override
        public String hash(String rawPassword) {
            return rawPassword;
        }
    }

}
