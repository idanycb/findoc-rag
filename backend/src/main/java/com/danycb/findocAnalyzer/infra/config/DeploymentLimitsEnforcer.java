package com.danycb.findocAnalyzer.infra.config;

import com.danycb.findocAnalyzer.infra.exception.LimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "findoc.limits.enabled", havingValue = "true")
public class DeploymentLimitsEnforcer implements DeploymentLimitsPort {

    private final FindocLimitsProperties properties;

    @Override
    public void assertCanAddDocument(LongSupplier currentCount) {
        Integer max = properties.getMaxDocuments();
        if (max == null) {
            return;
        }
        if (currentCount.getAsLong() >= max) {
            throw new LimitExceededException("Document limit reached (" + max + ")");
        }
    }

    @Override
    public void assertCanAddUser(LongSupplier currentCount) {
        Integer max = properties.getMaxUsers();
        if (max == null) {
            return;
        }
        if (currentCount.getAsLong() >= max) {
            throw new LimitExceededException("User limit reached (" + max + ")");
        }
    }

    @Override
    public void assertCanAddTeam(LongSupplier currentCount) {
        Integer max = properties.getMaxTeams();
        if (max == null) {
            return;
        }
        if (currentCount.getAsLong() >= max) {
            throw new LimitExceededException("Team limit reached (" + max + ")");
        }
    }

    @Override
    public void assertFileSizeAllowed(long bytes) {
        Long max = properties.getMaxFileSizeBytes();
        if (max == null) {
            return;
        }
        if (bytes > max) {
            throw new LimitExceededException("File size exceeds limit (" + max + " bytes)");
        }
    }
}
