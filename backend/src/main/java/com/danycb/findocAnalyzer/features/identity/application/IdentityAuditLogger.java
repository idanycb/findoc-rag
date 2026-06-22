package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IdentityAuditLogger {

    public void userCreated(AuthenticatedUser actor, User target) {
        logSuccess("user_created", actor, target);
    }

    public void userDeleted(AuthenticatedUser actor, User target) {
        logSuccess("user_deleted", actor, target);
    }

    public void userRoleChanged(AuthenticatedUser actor, User target, UserRole oldRole) {
        log.info(
                "event=user_role_changed actorUserId={} actorRole={} targetUserId={} targetUsername={} oldRole={} newRole={} teamId={}",
                actor.userId(), actor.role(),
                target.id(), target.username(), oldRole, target.role(), target.teamId());
    }

    public void superAdminOnboarded(User user) {
        log.info("event=super_admin_onboarded userId={} username={}", user.id(), user.username());
    }

    private void logSuccess(String event, AuthenticatedUser actor, User target) {
        log.info(
                "event={} actorUserId={} actorRole={} targetUserId={} targetUsername={} targetRole={} teamId={}",
                event, actor.userId(), actor.role(),
                target.id(), target.username(), target.role(), target.teamId());
    }
}
