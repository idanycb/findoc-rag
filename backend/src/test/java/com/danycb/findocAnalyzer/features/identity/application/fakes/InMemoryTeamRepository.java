package com.danycb.findocAnalyzer.features.identity.application.fakes;

import com.danycb.findocAnalyzer.features.identity.application.out.TeamPersistencePort;
import com.danycb.findocAnalyzer.features.identity.domain.Team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryTeamRepository implements TeamPersistencePort {
    private final Map<UUID, Team> store = new LinkedHashMap<>();

    public Team seed(Team team) {
        UUID id = team.id() != null ? team.id() : UUID.randomUUID();
        Team stored = new Team(id, team.name(),
                team.createdAt() != null ? team.createdAt() : Instant.now());
        store.put(id, stored);
        return stored;
    }

    @Override
    public Team save(Team team) {
        UUID id = team.id() != null ? team.id() : UUID.randomUUID();
        Team stored = new Team(id, team.name(),
                team.createdAt() != null ? team.createdAt() : Instant.now());
        store.put(id, stored);
        return stored;
    }

    @Override
    public Optional<Team> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Team> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean existsByName(String name) {
        return store.values().stream().anyMatch(t -> t.name().equals(name));
    }

    @Override
    public long countAll() {
        return store.size();
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }
}
