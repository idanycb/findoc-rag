package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.danycb.findocAnalyzer.features.vault.application.out.FilingSectionsPort;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class EdgarFilingSectionsAdapter implements FilingSectionsPort {
    private final RestClient restClient;

    public EdgarFilingSectionsAdapter(@Value("${edgar.url}") String edgarServiceUrl) {
        this.restClient = RestClient.create(edgarServiceUrl);
    }

    @Override
    public List<ParsedSection> fetchSections(String ticker, String accessionNumber) {
        FilingSectionsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/filings/sections")
                        .queryParam("ticker", ticker)
                        .queryParam("accession", accessionNumber)
                        .build())
                .retrieve()
                .body(FilingSectionsResponse.class);

        if (response == null || response.sections() == null || response.sections().isEmpty()) {
            throw new IllegalStateException("No EDGAR sections returned for " + ticker + " " + accessionNumber);
        }

        return response.sections().stream()
                .filter(section -> section.text() != null && !section.text().isBlank())
                .map(section -> new ParsedSection(section.pageNumber(), title(section), section.text().replace("\0", "")))
                .toList();
    }

    private String title(SectionResponse section) {
        if (section.title() != null && !section.title().isBlank()) {
            return section.title();
        }
        return section.item();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FilingSectionsResponse(
            @JsonProperty("sections") List<SectionResponse> sections
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SectionResponse(
            String item,
            String title,
            String text,
            @JsonProperty("pageNumber") Integer pageNumberValue
    ) {
        public int pageNumber() {
            return pageNumberValue == null || pageNumberValue < 1 ? 1 : pageNumberValue;
        }
    }
}
