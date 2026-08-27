package com.danycb.findocAnalyzer.features.chat.domain;

import java.time.LocalDate;

public record Citation(
        String accessionNumber,
        String formType,
        LocalDate filingDate,
        String sectionItem,
        String title,
        Integer page,
        String excerpt
) {
}
