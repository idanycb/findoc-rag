package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.FilingCatalogPort;
import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;
import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListFilingsServiceTest {
    private final RecordingFilingCatalog filingCatalog = new RecordingFilingCatalog();
    private final ListFilingsService service = new ListFilingsService(filingCatalog);

    @Test
    void list_delegatesCompanyIdAndFormTypeToFilingCatalogPort() {
        List<FilingResult> result = service.list("AAPL", "10-K");

        assertThat(filingCatalog.receivedCompanyId).isEqualTo("AAPL");
        assertThat(filingCatalog.receivedFormType).isEqualTo("10-K");
        assertThat(result).containsExactly(new FilingResult(
                "0000320193-24-000123",
                "10-K",
                LocalDate.of(2024, 11, 1),
                LocalDate.of(2024, 9, 28),
                "FY2024",
                "https://sec.example/aapl"));
    }

    static class RecordingFilingCatalog implements FilingCatalogPort {
        String receivedCompanyId;
        String receivedFormType;

        @Override
        public List<CompanyResult> searchCompanies(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FilingResult> listFilings(String companyId, String formType) {
            receivedCompanyId = companyId;
            receivedFormType = formType;
            return List.of(new FilingResult(
                    "0000320193-24-000123",
                    "10-K",
                    LocalDate.of(2024, 11, 1),
                    LocalDate.of(2024, 9, 28),
                    "FY2024",
                    "https://sec.example/aapl"));
        }
    }
}
