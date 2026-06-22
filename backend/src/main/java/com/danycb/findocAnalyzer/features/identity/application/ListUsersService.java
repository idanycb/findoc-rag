package com.danycb.findocAnalyzer.features.identity.application;

import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.in.ListUsersUseCase;
import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListUsersService implements ListUsersUseCase {
    private final UserReaderPort users;

    @Override
    @Transactional(readOnly = true)
    public List<User> list(AuthenticatedUser authenticatedUser) {
        return authenticatedUser.role() == UserRole.SUPER_ADMIN
                ? users.findAll()
                : users.findByTeamId(authenticatedUser.teamId());
    }
}
