package com.danycb.findocAnalyzer.infra.config;

import java.util.function.LongSupplier;

public interface DeploymentLimitsPort {

    void assertCanAddDocument(LongSupplier currentCount);

    void assertCanAddUser(LongSupplier currentCount);

    void assertCanAddTeam(LongSupplier currentCount);

    void assertFileSizeAllowed(long bytes);
}
