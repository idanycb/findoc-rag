package com.danycb.findocAnalyzer.features.identity.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.identity.application.out.TeamPersistencePort;
import com.danycb.findocAnalyzer.features.identity.domain.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TeamRepository implements TeamPersistencePort {
    private final TeamJpaRepository teamJpaRepo;

    @Override
    public Team save(Team team) {
        TeamJpaEntity entity = TeamJpaEntity.builder()
                .id(team.id())
                .name(team.name())
                .createdAt(team.createdAt())
                .build();
        return toDomain(teamJpaRepo.save(entity));
    }

    @Override
    public Optional<Team> findById(UUID id) {
        return teamJpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Team> findAll() {
        return teamJpaRepo.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByName(String name) {
        return teamJpaRepo.existsByName(name);
    }

    @Override
    public void deleteById(UUID id) {
        teamJpaRepo.deleteById(id);
    }

    private Team toDomain(TeamJpaEntity e) {
        return new Team(e.getId(), e.getName(), e.getCreatedAt());
    }
}

interface TeamJpaRepository extends JpaRepository<TeamJpaEntity, UUID> {
    boolean existsByName(String name);
}
