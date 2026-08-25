package com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto;

import java.util.List;

public record ChatResponse(String answer, List<CitationResponse> citations) {
}
