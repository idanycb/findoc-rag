package com.danycb.findocAnalyzer.features.identity.application.in;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;

import java.util.UUID;

public interface DeleteUserUseCase {
    void delete(AuthenticatedUser authenticatedUser, UUID targetUserId);
}
