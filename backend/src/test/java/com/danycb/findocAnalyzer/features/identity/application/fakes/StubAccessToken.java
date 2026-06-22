package com.danycb.findocAnalyzer.features.identity.application.fakes;

import com.danycb.findocAnalyzer.features.identity.application.out.AccessTokenPort;
import com.danycb.findocAnalyzer.features.identity.domain.User;

public class StubAccessToken implements AccessTokenPort {
    @Override
    public String generate(User user) {
        return "fake-token-for-" + user.username();
    }
}
