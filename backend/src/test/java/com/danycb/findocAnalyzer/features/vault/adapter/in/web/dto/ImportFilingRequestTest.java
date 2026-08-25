package com.danycb.findocAnalyzer.features.vault.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for {@link ImportFilingRequest#toCommand()} — the web-to-application field mapping. */
class ImportFilingRequestTest {

    @Test
    void toCommand_mapsEveryField() {
        ImportFilingRequest request = new ImportFilingRequest(
                "AAPL",
                "0000320193-24-000123",
                "0000320193-23-000099",
                "320193",
                "Apple Inc.",
                "10-K",
                "FY2024",
                LocalDate.of(2024, 9, 28),
                LocalDate.of(2024, 11, 1),
                "https://sec.example/aapl");

        ImportFilingCommand command = request.toCommand();

        assertThat(command.ticker()).isEqualTo("AAPL");
        assertThat(command.accessionNumber()).isEqualTo("0000320193-24-000123");
        assertThat(command.amendsAccessionNumber()).isEqualTo("0000320193-23-000099");
        assertThat(command.cik()).isEqualTo("320193");
        assertThat(command.companyName()).isEqualTo("Apple Inc.");
        assertThat(command.formType()).isEqualTo("10-K");
        assertThat(command.fiscalPeriod()).isEqualTo("FY2024");
        assertThat(command.reportDate()).isEqualTo(LocalDate.of(2024, 9, 28));
        assertThat(command.filingDate()).isEqualTo(LocalDate.of(2024, 11, 1));
        assertThat(command.sourceUrl()).isEqualTo("https://sec.example/aapl");
    }
}
