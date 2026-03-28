package com.danycb.findocAnalyzer.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.function.Function;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final String username;
    private final UUID userId;
    private final UUID tenantId;

    public static UUID getCurrentTenantId() {
        return extract(UserPrincipal::getTenantId);
    }

    public static UUID getCurrentUserId() {
        return extract(UserPrincipal::getUserId);
    }

    private static <T> T extract(Function<UserPrincipal, T> extractor) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal) {
            return extractor.apply((UserPrincipal) principal);
        }

        throw new IllegalStateException("Current user principal is not valid.");
    }
}
