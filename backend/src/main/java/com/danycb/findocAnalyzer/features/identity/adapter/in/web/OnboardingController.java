package com.danycb.findocAnalyzer.features.identity.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.adapter.in.web.dto.OnboardingStatusView;
import com.danycb.findocAnalyzer.features.identity.adapter.in.web.dto.UserView;
import com.danycb.findocAnalyzer.features.identity.application.dto.OnboardCommand;
import com.danycb.findocAnalyzer.features.identity.application.in.OnboardSuperAdminUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * One-time bootstrap of the initial super admin. Public, but the endpoint rejects the call once any
 * user exists.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {
    private final OnboardSuperAdminUseCase onboardSuperAdmin;

    @GetMapping("/status")
    public OnboardingStatusView status() {
        return new OnboardingStatusView(onboardSuperAdmin.isOnboardingEnabled());
    }

    @PostMapping
    public ResponseEntity<UserView> onboard(@RequestBody @Valid OnboardCommand command) {
        UserView created = UserView.from(onboardSuperAdmin.onboard(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
