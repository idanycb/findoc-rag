package com.danycb.findocAnalyzer.features.identity.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;

import java.util.UUID;

public record UserView(UUID id, String username, UserRole role, UUID teamId) {
    public static UserView from(User user) {
        return new UserView(user.id(), user.username(), user.role(), user.teamId());
    }
}
