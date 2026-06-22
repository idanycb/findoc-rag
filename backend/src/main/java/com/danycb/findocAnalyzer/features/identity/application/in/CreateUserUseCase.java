package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.dto.CreateUserCommand;
import com.danycb.findocAnalyzer.features.identity.domain.User;

public interface CreateUserUseCase {
    User create(AuthenticatedUser authenticatedUser, CreateUserCommand command);
}
