package com.danycb.findocAnalyzer.features.vault.application.out;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedPage;

import java.util.List;

public interface DocumentParserPort {
    List<ParsedPage> parse(byte[] content, String fileName, String contentType);
}
