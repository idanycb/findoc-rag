package com.danycb.findocAnalyzer.features.identity.application.fakes;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;

public class NoOpAuditLogger extends com.danycb.findocAnalyzer.features.identity.application.IdentityAuditLogger {
    @Override public void userCreated(AuthenticatedUser actor, User target) {}
    @Override public void userDeleted(AuthenticatedUser actor, User target) {}
    @Override public void userRoleChanged(AuthenticatedUser actor, User target, UserRole oldRole) {}
    @Override public void superAdminOnboarded(User user) {}
}
