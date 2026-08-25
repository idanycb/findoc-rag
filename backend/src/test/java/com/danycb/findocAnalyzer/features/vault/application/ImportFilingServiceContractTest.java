package com.danycb.findocAnalyzer.features.vault.application;

import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class ImportFilingServiceContractTest {

    @Test
    void publicationClaimsAreNotRetainedInProcessMemory() {
        assertThat(ImportFilingService.class.getDeclaredFields())
                .noneSatisfy(field -> assertThat(Collection.class.isAssignableFrom(field.getType()))
                        .as("publication claims must be database-backed, not a growing process-local collection")
                        .isTrue());
    }
}
