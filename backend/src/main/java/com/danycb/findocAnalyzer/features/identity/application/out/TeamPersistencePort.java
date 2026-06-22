package com.danycb.findocAnalyzer.features.identity.application.out;

import com.danycb.findocAnalyzer.features.identity.domain.Team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamPersistencePort {
    Team save(Team team);

    Optional<Team> findById(UUID id);

    List<Team> findAll();

    boolean existsByName(String name);

    void deleteById(UUID id);
}
