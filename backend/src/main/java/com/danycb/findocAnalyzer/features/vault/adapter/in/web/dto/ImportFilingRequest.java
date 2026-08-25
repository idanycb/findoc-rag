package com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record ImportFilingRequest(
        @NotBlank String ticker,
        @NotBlank String accessionNumber,
        String amendsAccessionNumber,
        String cik,
        String companyName,
        @NotBlank
        @Pattern(regexp = "10-K|10-K/A|10-Q|10-Q/A")
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
                amendsAccessionNumber,
                cik,
                companyName,
                formType,
                fiscalPeriod,
                reportDate,
                filingDate,
                sourceUrl);
    }
}
