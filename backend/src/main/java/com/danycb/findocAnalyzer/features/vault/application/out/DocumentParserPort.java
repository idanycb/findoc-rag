package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;

import java.util.List;

public interface DocumentParserPort {
    List<ParsedSection> parse(byte[] content, String fileName, String contentType);
}
