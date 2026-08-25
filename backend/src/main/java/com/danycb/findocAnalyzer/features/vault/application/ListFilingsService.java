package com.danycb.findocAnalyzer.features.vault.application;

import com.danycb.findocAnalyzer.features.vault.application.in.ListFilingsUseCase;
import com.danycb.findocAnalyzer.features.vault.application.out.FilingCatalogPort;
import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;
import com.danycb.findocAnalyzer.features.vault.domain.EdgarFormType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListFilingsService implements ListFilingsUseCase {
    private final FilingCatalogPort filingCatalog;

    @Override
    public List<FilingResult> list(String companyId, String formType) {
        EdgarFormType supportedForm = EdgarFormType.parse(formType);
        return filingCatalog.listFilings(companyId, supportedForm.value());
    }
}
