package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.domain.User;

import java.util.List;

public interface ListUsersUseCase {
    List<User> list(AuthenticatedUser authenticatedUser);
}
