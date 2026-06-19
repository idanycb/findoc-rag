package com.danycb.findocAnalyzer.features.identity.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.identity.application.out.UserLookupPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepository implements UserLookupPort {
    private final UserJpaRepository userJpaRepo;

    @Override
    public Optional<User> findByUsername(String username) {
        return userJpaRepo.findByUsername(username)
                .map(u -> new User(u.getId(), u.getUsername(), u.getPassword(), u.getRole()));
    }
}

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByUsername(String username);
}