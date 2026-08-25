package com.danycb.findocAnalyzer.features.vault.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EdgarFormTypeTest {

    @Test
    void parseAcceptsOnlyTheFourSidecarForms() {
        assertThat(EdgarFormType.parse("10-K")).isEqualTo(EdgarFormType.TEN_K);
        assertThat(EdgarFormType.parse("10-K/A")).isEqualTo(EdgarFormType.TEN_K_AMENDMENT);
        assertThat(EdgarFormType.parse("10-Q")).isEqualTo(EdgarFormType.TEN_Q);
        assertThat(EdgarFormType.parse("10-Q/A")).isEqualTo(EdgarFormType.TEN_Q_AMENDMENT);
    }

    @Test
    void parseRejectsUnsupportedBlankAndNonExactForms() {
        assertThatThrownBy(() -> EdgarFormType.parse("8-K")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EdgarFormType.parse("10-k")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EdgarFormType.parse(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EdgarFormType.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amendmentsExposeTheirBaseFormAndStatus() {
        assertThat(EdgarFormType.TEN_K_AMENDMENT.baseForm()).isEqualTo("10-K");
        assertThat(EdgarFormType.TEN_K_AMENDMENT.isAmendment()).isTrue();
        assertThat(EdgarFormType.TEN_Q_AMENDMENT.baseForm()).isEqualTo("10-Q");
        assertThat(EdgarFormType.TEN_Q_AMENDMENT.isAmendment()).isTrue();
        assertThat(EdgarFormType.TEN_K.isAmendment()).isFalse();
        assertThat(EdgarFormType.TEN_Q.isAmendment()).isFalse();
    }
}
