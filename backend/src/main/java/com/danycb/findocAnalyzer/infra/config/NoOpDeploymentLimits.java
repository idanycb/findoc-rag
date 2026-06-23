package com.danycb.findocAnalyzer.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@Component
@ConditionalOnProperty(name = "findoc.limits.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDeploymentLimits implements DeploymentLimitsPort {

    @Override
    public void assertCanAddDocument(LongSupplier currentCount) {
        // no-op
    }

    @Override
    public void assertCanAddUser(LongSupplier currentCount) {
        // no-op
    }

    @Override
    public void assertCanAddTeam(LongSupplier currentCount) {
        // no-op
    }

    @Override
    public void assertFileSizeAllowed(long bytes) {
        // no-op
    }
}
