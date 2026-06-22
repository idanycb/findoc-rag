package com.danycb.findocAnalyzer.features.identity.application.out;

import com.danycb.findocAnalyzer.features.identity.domain.User;

import java.util.UUID;

public interface UserWriterPort {
    User save(User user);

    void deleteById(UUID id);
}
