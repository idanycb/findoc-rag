package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.dto.ChangeRoleCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.exception.NotFoundException;
import com.danycb.findocAnalyzer.features.identity.application.in.ChangeUserRoleUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserWriterPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Changes a user's role between ADMIN and MEMBER. A SUPER_ADMIN may promote or demote freely; an
 * ADMIN may only promote a MEMBER to ADMIN within their own team (no demotion). The SUPER_ADMIN role
 * can neither be assigned nor removed through this operation.
 */
@Service
@RequiredArgsConstructor
public class ChangeUserRoleService implements ChangeUserRoleUseCase {
    private final UserWriterPort userWriter;
    private final UserReaderPort userQueries;
    private final IdentityAuditLogger audit;

    @Override
    @Transactional
    public User changeRole(AuthenticatedUser authenticatedUser, UUID targetUserId, ChangeRoleCommand command) {
        UserRole newRole = command.role();
        if (newRole == UserRole.SUPER_ADMIN) {
            throw new ForbiddenOperationException("Cannot assign the super admin role");
        }

        User target = userQueries.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetUserId));

        if (target.role() == UserRole.SUPER_ADMIN) {
            throw new ForbiddenOperationException("Cannot change the role of a super admin");
        }

        if (authenticatedUser.role() == UserRole.ADMIN) {
            if (target.teamId() == null || !target.teamId().equals(authenticatedUser.teamId())) {
                throw new ForbiddenOperationException("You can only manage users in your own team");
            }
            boolean promotingMemberToAdmin =
                    target.role() == UserRole.MEMBER && newRole == UserRole.ADMIN;
            if (!promotingMemberToAdmin) {
                throw new ForbiddenOperationException(
                        "Admins may only promote members to admin; demotion requires a super admin");
            }
        }
        // SUPER_ADMIN: any ADMIN<->MEMBER transition is allowed

        UserRole oldRole = target.role();
        User updated = new User(
                target.id(), target.username(), target.passwordHash(), newRole, target.teamId());
        User saved = userWriter.save(updated);
        audit.userRoleChanged(authenticatedUser, saved, oldRole);
        return saved;
    }
}
