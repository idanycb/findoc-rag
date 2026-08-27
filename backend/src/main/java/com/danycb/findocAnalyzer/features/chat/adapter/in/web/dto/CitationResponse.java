package com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto;

import com.danycb.findocAnalyzer.features.chat.domain.Citation;

import java.time.LocalDate;

public record CitationResponse(
        int number,
        String accessionNumber,
        String formType,
        LocalDate filingDate,
        String sectionItem,
        String title,
        Integer page,
        String excerpt
) {
    public static CitationResponse from(Citation citation, int number) {
        return new CitationResponse(
                number,
                citation.accessionNumber(),
                citation.formType(),
                citation.filingDate(),
                citation.sectionItem(),
                citation.title(),
                citation.page(),
                citation.excerpt());
    }
}
