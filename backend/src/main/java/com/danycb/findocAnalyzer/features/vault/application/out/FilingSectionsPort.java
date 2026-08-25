package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;

public interface FilingSectionsPort {
    FilingSectionsResult fetchSections(String ticker, String accessionNumber);
}
