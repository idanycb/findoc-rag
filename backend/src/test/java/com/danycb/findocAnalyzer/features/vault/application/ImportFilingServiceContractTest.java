package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentAnalysisMessage;
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

    @Test
    void filingImportsDependOnTheDurableOutboxInsteadOfTheAnalysisQueue() throws Exception {
        Class<?> outboxPort = analysisOutboxPort();

        assertThat(outboxPort.getMethod("enqueue", DocumentAnalysisMessage.class).getReturnType())
                .isEqualTo(void.class);
        assertThat(ImportFilingService.class.getDeclaredFields())
                .anySatisfy(field -> assertThat(field.getType()).isEqualTo(outboxPort))
                .noneSatisfy(field -> assertThat(field.getType().getSimpleName()).isEqualTo("AnalysisQueuePort"));
    }

    private Class<?> analysisOutboxPort() {
        try {
            return Class.forName("com.danycb.findocAnalyzer.features.vault.application.out.AnalysisOutboxPort");
        } catch (ClassNotFoundException missingOutbox) {
            throw new AssertionError("EDGAR imports need a durable AnalysisOutboxPort", missingOutbox);
        }
    }
}
