package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;

import java.util.List;

public interface FilingSectionsPort {
    List<ParsedSection> fetchSections(String ticker, String accessionNumber);
}
