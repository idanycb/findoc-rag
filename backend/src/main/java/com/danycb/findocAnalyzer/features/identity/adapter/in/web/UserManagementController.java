package com.danycb.findocAnalyzer.features.identity.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.adapter.in.web.dto.UserView;
import com.danycb.findocAnalyzer.features.identity.application.dto.AuthenticatedUser;
import com.danycb.findocAnalyzer.features.identity.application.dto.ChangeRoleCommand;
import com.danycb.findocAnalyzer.features.identity.application.dto.CreateUserCommand;
import com.danycb.findocAnalyzer.features.identity.application.in.ChangeUserRoleUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.CreateUserUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.DeleteUserUseCase;
import com.danycb.findocAnalyzer.features.identity.application.in.ListUsersUseCase;
import com.danycb.findocAnalyzer.infra.security.RequireAdminOrSuperAdmin;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Team membership management. Coarse access (SUPER_ADMIN or ADMIN) is enforced here; the
 * fine-grained team/role rules live in the services, driven by the {@link AuthenticatedUser}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@RequireAdminOrSuperAdmin
public class UserManagementController {
    private final CreateUserUseCase createUser;
    private final ListUsersUseCase listUsers;
    private final DeleteUserUseCase deleteUser;
    private final ChangeUserRoleUseCase changeUserRole;

    @PostMapping
    public ResponseEntity<UserView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid CreateUserCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserView.from(createUser.create(toAuthenticatedUser(principal), command)));
    }

    @GetMapping
    public List<UserView> list(@AuthenticationPrincipal UserPrincipal principal) {
        return listUsers.list(toAuthenticatedUser(principal)).stream().map(UserView::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        deleteUser.delete(toAuthenticatedUser(principal), id);
    }

    @PatchMapping("/{id}/role")
    public UserView changeRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody @Valid ChangeRoleCommand command) {
        return UserView.from(changeUserRole.changeRole(toAuthenticatedUser(principal), id, command));
    }

    private AuthenticatedUser toAuthenticatedUser(UserPrincipal principal) {
        return new AuthenticatedUser(principal.userId(), principal.role(), principal.teamId());
    }
}
