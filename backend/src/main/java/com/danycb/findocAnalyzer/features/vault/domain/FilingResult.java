package com.danycb.findocAnalyzer.features.vault.domain;

import java.time.LocalDate;

public record FilingResult(
        String accessionNumber,
        String form,
        LocalDate filingDate,
        LocalDate reportDate,
        String fiscalPeriod,
        String sourceUrl
) {
}
