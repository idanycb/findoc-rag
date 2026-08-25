package com.danycb.findocAnalyzer.features.vault.domain;

public record ParsedSection(int pageNumber, String item, String title, String text) {
    public ParsedSection(int pageNumber, String title, String text) {
        this(pageNumber, null, title, text);
    }

    public ParsedSection(int pageNumber, String text) {
        this(pageNumber, null, null, text);
    }
}
