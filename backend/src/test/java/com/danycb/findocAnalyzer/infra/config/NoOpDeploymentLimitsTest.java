package com.danycb.findocAnalyzer.infra.config;

import com.danycb.findocAnalyzer.infra.exception.LimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpDeploymentLimitsTest {

    private final NoOpDeploymentLimits limits = new NoOpDeploymentLimits();

    @Test
    void assertCanAddDocument_doesNotInvokeSupplier() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        assertThatCode(() -> limits.assertCanAddDocument(() -> {
            invoked.set(true);
            return 999;
        })).doesNotThrowAnyException();

        assertThat(invoked).isFalse();
    }

    @Test
    void assertCanAddUser_doesNotInvokeSupplier() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        assertThatCode(() -> limits.assertCanAddUser(() -> {
            invoked.set(true);
            return 999;
        })).doesNotThrowAnyException();

        assertThat(invoked).isFalse();
    }

    @Test
    void assertCanAddTeam_doesNotInvokeSupplier() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        assertThatCode(() -> limits.assertCanAddTeam(() -> {
            invoked.set(true);
            return 999;
        })).doesNotThrowAnyException();

        assertThat(invoked).isFalse();
    }

    @Test
    void assertFileSizeAllowed_isNoOp() {
        assertThatCode(() -> limits.assertFileSizeAllowed(Long.MAX_VALUE)).doesNotThrowAnyException();
    }
}
