package com.danycb.findocAnalyzer.features.identity.application.fakes;

import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserWriterPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryUserRepository
        implements UserReaderPort, UserWriterPort {
    private final Map<UUID, User> store = new LinkedHashMap<>();

    @Override
    public Optional<User> findByUsername(String username) {
        return store.values().stream().filter(u -> u.username().equals(username)).findFirst();
    }

    /** Seed a user with an explicit id, returning the stored copy. */
    public User seed(User user) {
        UUID id = user.id() != null ? user.id() : UUID.randomUUID();
        User stored = new User(id, user.username(), user.passwordHash(), user.role(), user.teamId());
        store.put(id, stored);
        return stored;
    }

    @Override
    public User save(User user) {
        UUID id = user.id() != null ? user.id() : UUID.randomUUID();
        User stored = new User(id, user.username(), user.passwordHash(), user.role(), user.teamId());
        store.put(id, stored);
        return stored;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<User> findByTeamId(UUID teamId) {
        return store.values().stream().filter(u -> teamId.equals(u.teamId())).toList();
    }

    @Override
    public boolean existsAny() {
        return !store.isEmpty();
    }

    @Override
    public boolean existsByUsername(String username) {
        return store.values().stream().anyMatch(u -> u.username().equals(username));
    }

    @Override
    public long countByRole(UserRole role) {
        return store.values().stream().filter(u -> u.role() == role).count();
    }

    @Override
    public long countByTeamId(UUID teamId) {
        return store.values().stream().filter(u -> teamId.equals(u.teamId())).count();
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }
}
