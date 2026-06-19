package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.LoginCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.LoginResult;

public interface LoginUseCase {
    LoginResult login(LoginCommand command);
}
