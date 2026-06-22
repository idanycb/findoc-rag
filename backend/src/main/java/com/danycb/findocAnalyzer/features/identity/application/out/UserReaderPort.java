package com.danycb.findocAnalyzer.features.identity.application.out;

import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserReaderPort {
    Optional<User> findByUsername(String username);

    Optional<User> findById(UUID id);

    List<User> findAll();

    List<User> findByTeamId(UUID teamId);

    boolean existsAny();

    boolean existsByUsername(String username);

    long countByRole(UserRole role);

    long countByTeamId(UUID teamId);
}
