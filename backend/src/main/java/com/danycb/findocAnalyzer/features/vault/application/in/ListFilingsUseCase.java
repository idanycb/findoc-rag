package com.danycb.findocAnalyzer.features.vault.application.in;

import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;

import java.util.List;

public interface ListFilingsUseCase {
    List<FilingResult> list(String companyId, String formType);
}
