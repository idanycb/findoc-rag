package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.danycb.findocAnalyzer.features.vault.application.out.FilingSectionsPort;
import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;

@Component
public class EdgarFilingSectionsAdapter implements FilingSectionsPort {
    private final RestClient restClient;

    public EdgarFilingSectionsAdapter(@Value("${edgar.url}") String edgarServiceUrl) {
        this.restClient = RestClient.create(edgarServiceUrl);
    }

    @Override
    public FilingSectionsResult fetchSections(String ticker, String accessionNumber) {
        FilingSectionsResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/filings/sections")
                            .queryParam("ticker", ticker)
                            .queryParam("accession", accessionNumber)
                            .build())
                    .retrieve()
                    .body(FilingSectionsResponse.class);
        } catch (RestClientResponseException failure) {
            throw EdgarErrorTranslator.translate(failure);
        } catch (ResourceAccessException failure) {
            throw EdgarErrorTranslator.unavailable(failure);
        }

        if (response == null || response.filing() == null) {
            throw new IllegalStateException("Incomplete EDGAR filing response for " + ticker + " " + accessionNumber);
        }
        if (response.hasSearchableSections() == null) {
            throw new IllegalStateException("EDGAR response is missing hasSearchableSections");
        }

        FilingResponse filing = response.filing();
        requireNonBlank(filing.accessionNumber(), "accessionNumber");
        requireNonBlank(filing.form(), "form");

        List<ParsedSection> sections = response.sections() == null
                ? List.of()
                : response.sections().stream()
                        .filter(section -> section.text() != null && !section.text().isBlank())
                        .map(section -> new ParsedSection(
                                section.pageNumber(),
                                blankToNull(section.item()),
                                blankToNull(section.title()),
                                section.text().replace("\0", "")))
                        .filter(section -> !section.text().isBlank())
                        .toList();

        if (response.hasSearchableSections() && sections.isEmpty()) {
            throw new IllegalStateException("EDGAR response marked searchable but contained no usable sections");
        }

        return new FilingSectionsResult(
                filing.accessionNumber(),
                filing.amendsAccessionNumber(),
                filing.form(),
                filing.filingDate(),
                filing.reportDate(),
                response.hasSearchableSections(),
                response.hasSearchableSections() ? sections : List.of());
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("EDGAR response has blank " + field);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FilingSectionsResponse(
            FilingResponse filing,
            @JsonProperty("sections") List<SectionResponse> sections,
            @JsonProperty("hasSearchableSections") Boolean hasSearchableSections
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FilingResponse(
            @JsonProperty("accessionNumber") String accessionNumber,
            @JsonProperty("amendsAccessionNumber") String amendsAccessionNumber,
            String form,
            @JsonProperty("filingDate") LocalDate filingDate,
            @JsonProperty("reportDate") LocalDate reportDate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SectionResponse(
            String item,
            String title,
            String text,
            @JsonProperty("pageNumber") Integer pageNumber
    ) {
    }
}
