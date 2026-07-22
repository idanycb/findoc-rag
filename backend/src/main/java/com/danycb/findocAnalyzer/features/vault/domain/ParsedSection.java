package com.danycb.findocAnalyzer.features.vault.domain;

public record ParsedSection(int pageNumber, String title, String text) {
    public ParsedSection(int pageNumber, String text) {
        this(pageNumber, null, text);
    }
}
