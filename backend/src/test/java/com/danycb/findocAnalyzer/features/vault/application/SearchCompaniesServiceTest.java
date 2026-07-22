package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.out.FilingCatalogPort;
import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;
import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCompaniesServiceTest {
    private final RecordingFilingCatalog filingCatalog = new RecordingFilingCatalog();
    private final SearchCompaniesService service = new SearchCompaniesService(filingCatalog);

    @Test
    void search_delegatesQueryToFilingCatalogPort() {
        List<CompanyResult> result = service.search("apple");

        assertThat(filingCatalog.receivedQuery).isEqualTo("apple");
        assertThat(result).containsExactly(new CompanyResult("AAPL", "320193", "Apple Inc."));
    }

    static class RecordingFilingCatalog implements FilingCatalogPort {
        String receivedQuery;

        @Override
        public List<CompanyResult> searchCompanies(String query) {
            receivedQuery = query;
            return List.of(new CompanyResult("AAPL", "320193", "Apple Inc."));
        }

        @Override
        public List<FilingResult> listFilings(String companyId, String formType) {
            throw new UnsupportedOperationException();
        }
    }
}
