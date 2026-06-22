package com.danycb.findocAnalyzer.features.identity.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.adapter.in.web.dto.TeamView;
import com.danycb.findocAnalyzer.features.identity.application.dto.TeamCommand;
import com.danycb.findocAnalyzer.features.identity.application.in.CreateTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.DeleteTeamUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.ListTeamsUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.UpdateTeamUseCase;
import com.danycb.findocAnalyzer.infra.security.RequireSuperAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams")
@RequireSuperAdmin
public class TeamController {
    private final CreateTeamUseCase createTeam;
    private final ListTeamsUseCase listTeams;
    private final UpdateTeamUseCase updateTeam;
    private final DeleteTeamUseCase deleteTeam;

    @PostMapping
    public ResponseEntity<TeamView> create(@RequestBody @Valid TeamCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TeamView.from(createTeam.create(command)));
    }

    @GetMapping
    public List<TeamView> list() {
        return listTeams.list().stream().map(TeamView::from).toList();
    }

    @PutMapping("/{id}")
    public TeamView update(@PathVariable UUID id, @RequestBody @Valid TeamCommand command) {
        return TeamView.from(updateTeam.update(id, command));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteTeam.delete(id);
    }
}
