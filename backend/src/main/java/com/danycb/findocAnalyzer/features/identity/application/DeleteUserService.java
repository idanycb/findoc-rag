package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.exception.NotFoundException;
import com.danycb.findocAnalyzer.features.identity.application.in.DeleteUserUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserWriterPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Deletes a user. A SUPER_ADMIN may delete anyone except the last super admin; an ADMIN may only
 * delete a MEMBER within their own team. Nobody can delete themselves (avoids self-lockout).
 */
@Service
@RequiredArgsConstructor
public class DeleteUserService implements DeleteUserUseCase {
    private final UserWriterPort userWriter;
    private final UserReaderPort userReader;
    private final IdentityAuditLogger audit;

    @Override
    @Transactional
    public void delete(AuthenticatedUser authenticatedUser, UUID targetUserId) {
        User target = userReader.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetUserId));

        if (target.id().equals(authenticatedUser.userId())) {
            throw new ForbiddenOperationException("You cannot delete your own account");
        }

        if (authenticatedUser.role() == UserRole.ADMIN) {
            if (!target.teamId().equals(authenticatedUser.teamId())) {
                throw new ForbiddenOperationException("You can only manage users in your own team");
            }
            if (target.role() == UserRole.ADMIN) {
                throw new ForbiddenOperationException("Admins cannot delete other admins");
            }
        }

        userWriter.deleteById(targetUserId);
        audit.userDeleted(authenticatedUser, target);
    }
}
