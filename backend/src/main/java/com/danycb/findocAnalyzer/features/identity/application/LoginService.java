package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.LoginCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.LoginResult;
import com.danycb.findocAnalyzer.features.identity.application.in.LoginUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.AccessTokenPort;
import com.danycb.findocAnalyzer.features.identity.application.out.PasswordVerifierPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserLookupPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {
    private final UserLookupPort userLookup;
    private final PasswordVerifierPort passwordVerifier;
    private final AccessTokenPort accessToken;

    @Override
    public LoginResult login(LoginCommand command) {
        User user = userLookup.findByUsername(command.username())
                .filter(u -> passwordVerifier.matches(command.password(), u.passwordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        String token = accessToken.generate(user.id(), user.username());
        return new LoginResult(token);
    }
}
