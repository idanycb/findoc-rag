package com.danycb.findocAnalyzer.infra.config;

import com.danycb.findocAnalyzer.infra.exception.LimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentLimitsEnforcerTest {

    private FindocLimitsProperties properties;
    private DeploymentLimitsEnforcer enforcer;

    @BeforeEach
    void setUp() {
        properties = new FindocLimitsProperties();
        properties.setEnabled(true);
        enforcer = new DeploymentLimitsEnforcer(properties);
    }

    @Test
    void assertCanAddDocument_atCap_throws() {
        properties.setMaxDocuments(15);

        assertThatThrownBy(() -> enforcer.assertCanAddDocument(() -> 15))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("Document limit reached (15)");
    }

    @Test
    void assertCanAddDocument_belowCap_succeeds() {
        properties.setMaxDocuments(15);

        assertThatCode(() -> enforcer.assertCanAddDocument(() -> 14)).doesNotThrowAnyException();
    }

    @Test
    void assertCanAddDocument_whenLimitUnset_doesNotInvokeSupplier() {
        properties.setMaxDocuments(null);
        AtomicBoolean invoked = new AtomicBoolean(false);

        assertThatCode(() -> enforcer.assertCanAddDocument(() -> {
            invoked.set(true);
            return 999;
        })).doesNotThrowAnyException();

        assertThat(invoked).isFalse();
    }

    @Test
    void assertCanAddUser_atCap_throws() {
        properties.setMaxUsers(10);

        assertThatThrownBy(() -> enforcer.assertCanAddUser(() -> 10))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("User limit reached (10)");
    }

    @Test
    void assertCanAddTeam_atCap_throws() {
        properties.setMaxTeams(3);

        assertThatThrownBy(() -> enforcer.assertCanAddTeam(() -> 3))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("Team limit reached (3)");
    }

    @Test
    void assertFileSizeAllowed_exceedsMax_throws() {
        properties.setMaxFileSizeBytes(5242880L);

        assertThatThrownBy(() -> enforcer.assertFileSizeAllowed(5242881L))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("File size exceeds limit");
    }

    @Test
    void assertFileSizeAllowed_withinMax_succeeds() {
        properties.setMaxFileSizeBytes(5242880L);

        assertThatCode(() -> enforcer.assertFileSizeAllowed(5242880L)).doesNotThrowAnyException();
    }
}
