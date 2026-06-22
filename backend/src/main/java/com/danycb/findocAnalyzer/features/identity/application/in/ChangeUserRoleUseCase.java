package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.dto.ChangeRoleCommand;
import com.danycb.findocAnalyzer.features.identity.domain.User;

import java.util.UUID;

public interface ChangeUserRoleUseCase {
    User changeRole(AuthenticatedUser authenticatedUser, UUID targetUserId, ChangeRoleCommand command);
}
