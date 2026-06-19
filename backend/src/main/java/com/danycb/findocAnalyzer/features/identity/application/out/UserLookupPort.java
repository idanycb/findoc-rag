package com.danycb.findocAnalyzer.features.identity.application.out;

import com.danycb.findocAnalyzer.features.identity.domain.User;

import java.util.Optional;

public interface UserLookupPort {
    Optional<User> findByUsername(String username);
}
