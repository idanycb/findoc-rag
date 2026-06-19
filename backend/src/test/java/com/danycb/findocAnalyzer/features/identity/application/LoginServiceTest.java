package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.LoginCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.LoginResult;
import com.danycb.findocAnalyzer.features.identity.adapter.out.security.JwtAccessTokenAdapter;
import com.danycb.findocAnalyzer.features.identity.application.out.PasswordVerifierPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserLookupPort;
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
    private final User alice = new User(userId, "alice", "hashed-pw", UserRole.MEMBER);

    @BeforeEach
    void setUp() {
        var accessToken = new JwtAccessTokenAdapter("test-secret-that-is-at-least-32-bytes-long!!");
        loginService = new LoginService(userLookup, new PlaintextPasswordVerifier(), accessToken);
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

    static class FakeUserLookup implements UserLookupPort {
        private final Map<String, User> users = new HashMap<>();

        void add(User user) {
            users.put(user.username(), user);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(users.get(username));
        }
    }

    static class PlaintextPasswordVerifier implements PasswordVerifierPort {
        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return rawPassword.equals(encodedPassword);
        }
    }

}
