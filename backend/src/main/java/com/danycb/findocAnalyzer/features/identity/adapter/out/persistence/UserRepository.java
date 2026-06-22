package com.danycb.findocAnalyzer.features.identity.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.identity.application.out.UserReaderPort;
import com.danycb.findocAnalyzer.features.identity.application.out.UserWriterPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepository
        implements UserReaderPort, UserWriterPort {
    private final UserJpaRepository userJpaRepo;

    @Override
    public Optional<User> findByUsername(String username) {
        return userJpaRepo.findByUsername(username).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.builder()
                .id(user.id())
                .username(user.username())
                .password(user.passwordHash())
                .role(user.role())
                .teamId(user.teamId())
                .build();
        return toDomain(userJpaRepo.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<User> findAll() {
        return userJpaRepo.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<User> findByTeamId(UUID teamId) {
        return userJpaRepo.findByTeamId(teamId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsAny() {
        return userJpaRepo.count() > 0;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepo.existsByUsername(username);
    }

    @Override
    public long countByRole(UserRole role) {
        return userJpaRepo.countByRole(role);
    }

    @Override
    public long countByTeamId(UUID teamId) {
        return userJpaRepo.countByTeamId(teamId);
    }

    @Override
    public void deleteById(UUID id) {
        userJpaRepo.deleteById(id);
    }

    private User toDomain(UserJpaEntity u) {
        return new User(u.getId(), u.getUsername(), u.getPassword(), u.getRole(), u.getTeamId());
    }
}

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByUsername(String username);

    List<UserJpaEntity> findByTeamId(UUID teamId);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);

    long countByTeamId(UUID teamId);
}
