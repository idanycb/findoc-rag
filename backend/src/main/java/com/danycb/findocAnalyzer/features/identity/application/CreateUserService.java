package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.dto.CreateUserCommand;
import com.danycb.findocAnalyzer.features.identity.application.exception.DuplicateUsernameException;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.exception.NotFoundException;
import com.danycb.findocAnalyzer.features.identity.application.in.CreateUserUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.PasswordEncoderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.TeamPersistencePort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserWriterPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a team-scoped user. A SUPER_ADMIN may create an ADMIN or MEMBER in any team; an ADMIN may
 * only create a MEMBER in their own team. Creating another SUPER_ADMIN is never allowed here
 * (the single bootstrap super admin comes from onboarding).
 */
@Service
@RequiredArgsConstructor
public class CreateUserService implements CreateUserUseCase {
    private final UserWriterPort user;
    private final UserReaderPort userExistence;
    private final TeamPersistencePort teams;
    private final PasswordEncoderPort passwordEncoder;
    private final IdentityAuditLogger audit;

    @Override
    @Transactional
    public User create(AuthenticatedUser authenticatedUser, CreateUserCommand command) {
        UserRole targetRole;
        UUID targetTeamId;

        if (authenticatedUser.role() == UserRole.SUPER_ADMIN) {
            targetRole = command.role();
            if (targetRole == null) {
                throw new IllegalArgumentException("role is required");
            }
            if (targetRole == UserRole.SUPER_ADMIN) {
                throw new ForbiddenOperationException("Cannot create another super admin");
            }
            targetTeamId = command.teamId();
            if (targetTeamId == null) {
                throw new IllegalArgumentException("teamId is required");
            }
            if (teams.findById(targetTeamId).isEmpty()) {
                throw new NotFoundException("Team not found: " + targetTeamId);
            }
        } else { // ADMIN: always a MEMBER in the admin's own team
            targetRole = UserRole.MEMBER;
            targetTeamId = authenticatedUser.teamId();
        }

        if (userExistence.existsByUsername(command.username())) {
            throw new DuplicateUsernameException("Username already taken: " + command.username());
        }

        User created = new User(
                null,
                command.username(),
                passwordEncoder.hash(command.password()),
                targetRole,
                targetTeamId);

        User saved = user.save(created);
        audit.userCreated(authenticatedUser, saved);
        return saved;
    }
}
