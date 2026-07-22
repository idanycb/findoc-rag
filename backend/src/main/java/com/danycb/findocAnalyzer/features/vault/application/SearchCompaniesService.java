package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.SearchCompaniesUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.FilingCatalogPort;
import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchCompaniesService implements SearchCompaniesUseCase {
    private final FilingCatalogPort filingCatalog;

    @Override
    public List<CompanyResult> search(String query) {
        return filingCatalog.searchCompanies(query);
    }
}
