package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;
import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;

import java.util.List;

public interface FilingCatalogPort {
    List<CompanyResult> searchCompanies(String query);

    List<FilingResult> listFilings(String companyId, String formType);
}
