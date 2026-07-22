package com.danycb.findocAnalyzer.features.vault.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ImportFilingCommand(
        @NotBlank String ticker,
        @NotBlank String accessionNumber,
        String cik,
        String companyName,
        String formType,
        String fiscalPeriod,
        LocalDate reportDate,
        LocalDate filingDate,
        String sourceUrl
) {
}
