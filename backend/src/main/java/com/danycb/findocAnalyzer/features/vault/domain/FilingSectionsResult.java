package com.danycb.findocAnalyzer.features.vault.domain;

import java.time.LocalDate;
import java.util.List;

public record FilingSectionsResult(
        String accessionNumber,
        String amendsAccessionNumber,
        String formType,
        LocalDate filingDate,
        LocalDate reportDate,
        boolean hasSearchableSections,
        List<ParsedSection> sections
) {
    public FilingSectionsResult {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
