package com.danycb.findocAnalyzer.features.identity.adapter.out.persistence;

import com.danycb.findocAnalyzer.features.identity.application.out.OnboardingLockPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresOnboardingLock implements OnboardingLockPort {
    private static final String LOCK_NAME = "findoc:onboarding";

    private final EntityManager entityManager;

    @Override
    public void acquire() {
        entityManager
                .createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(:lockName, 0))")
                .setParameter("lockName", LOCK_NAME)
                .getSingleResult();
    }
}
