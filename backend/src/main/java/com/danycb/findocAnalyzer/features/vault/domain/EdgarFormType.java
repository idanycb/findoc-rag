package com.danycb.findocAnalyzer.features.vault.domain;

import java.util.Arrays;

public enum EdgarFormType {
    TEN_K("10-K", "10-K", false),
    TEN_K_AMENDMENT("10-K/A", "10-K", true),
    TEN_Q("10-Q", "10-Q", false),
    TEN_Q_AMENDMENT("10-Q/A", "10-Q", true);

    private final String value;
    private final String baseForm;
    private final boolean amendment;

    EdgarFormType(String value, String baseForm, boolean amendment) {
        this.value = value;
        this.baseForm = baseForm;
        this.amendment = amendment;
    }

    public static EdgarFormType parse(String value) {
        return Arrays.stream(values())
                .filter(form -> form.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported EDGAR form: " + value));
    }

    public String value() {
        return value;
    }

    public String baseForm() {
        return baseForm;
    }

    public boolean isAmendment() {
        return amendment;
    }
}
