package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.danycb.findocAnalyzer.features.vault.application.EdgarServiceUnavailableException;
import com.danycb.findocAnalyzer.features.vault.application.ResourceNotFoundException;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EdgarErrorTranslator {
    private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"([^\"]+)\"");

    private EdgarErrorTranslator() {
    }

    static RuntimeException translate(RestClientResponseException failure) {
        String detail = detail(failure.getResponseBodyAsString());
        return switch (failure.getStatusCode().value()) {
            case 404 -> new ResourceNotFoundException(detail == null ? "EDGAR resource not found" : detail);
            case 422 -> new IllegalArgumentException(detail == null ? "Invalid EDGAR request" : detail);
            default -> {
                if (failure.getStatusCode().is5xxServerError()) {
                    yield new EdgarServiceUnavailableException("EDGAR temporarily unavailable", failure);
                }
                yield failure;
            }
        };
    }

    static EdgarServiceUnavailableException unavailable(RuntimeException failure) {
        return new EdgarServiceUnavailableException("EDGAR temporarily unavailable", failure);
    }

    private static String detail(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        Matcher matcher = DETAIL.matcher(responseBody);
        return matcher.find() ? matcher.group(1) : null;
    }
}
