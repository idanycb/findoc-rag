package com.danycb.findocAnalyzer.features.identity.application.out;

import com.danycb.findocAnalyzer.features.identity.domain.User;

public interface AccessTokenPort {
    String generate(User user);
}
