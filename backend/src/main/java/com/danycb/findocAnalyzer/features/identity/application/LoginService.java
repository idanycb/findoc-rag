package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.LoginCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.LoginResult;
import com.danycb.findocAnalyzer.features.identity.application.exception.InvalidCredentialsException;
import com.danycb.findocAnalyzer.features.identity.application.in.LoginUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.AccessTokenPort;
import com.danycb.findocAnalyzer.features.identity.application.out.PasswordEncoderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {
    private final UserReaderPort userReader;
    private final PasswordEncoderPort passwordEncoder;
    private final AccessTokenPort accessToken;

    @Override
    public LoginResult login(LoginCommand command) {
        User user = userReader.findByUsername(command.username())
                .filter(u -> passwordEncoder.matches(command.password(), u.passwordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        String token = accessToken.generate(user);
        return new LoginResult(token);
    }
}
