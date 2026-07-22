package com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ImportFilingRequest(
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
    public ImportFilingCommand toCommand() {
        return new ImportFilingCommand(
                ticker,
                accessionNumber,
                cik,
                companyName,
                formType,
                fiscalPeriod,
                reportDate,
                filingDate,
                sourceUrl);
    }
}
