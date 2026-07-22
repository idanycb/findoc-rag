package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;

import java.util.List;

public interface SearchCompaniesUseCase {
    List<CompanyResult> search(String query);
}
