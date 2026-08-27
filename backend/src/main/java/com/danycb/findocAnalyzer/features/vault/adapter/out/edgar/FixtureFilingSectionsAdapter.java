package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.danycb.findocAnalyzer.features.vault.application.out.FilingSectionsPort;
import com.danycb.findocAnalyzer.features.vault.domain.FilingSectionsResult;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Component
@Primary
@Profile("eval")
public class FixtureFilingSectionsAdapter implements FilingSectionsPort {
    private final ObjectMapper objectMapper;
    private final Path corpusDirectory;

    public FixtureFilingSectionsAdapter(
            ObjectMapper objectMapper,
            @Value("${findoc.eval.corpus-directory:../evals/corpus/tesla-2025}") Path corpusDirectory) {
        this.objectMapper = objectMapper;
        this.corpusDirectory = corpusDirectory;
    }

    @Override
    public FilingSectionsResult fetchSections(String ticker, String accessionNumber) {
        Path fixture = corpusDirectory.resolve(accessionNumber + ".json").normalize();
        if (!fixture.startsWith(corpusDirectory.normalize())) {
            throw new IllegalArgumentException("Invalid fixture accession");
        }
        try {
            FixtureResponse response = objectMapper.readValue(fixture.toFile(), FixtureResponse.class);
            if (response.filing() == null || response.hasSearchableSections() == null) {
                throw new IllegalStateException("Incomplete filing fixture " + fixture);
            }
            List<ParsedSection> sections = response.sections() == null ? List.of() : response.sections().stream()
                    .filter(section -> section.text() != null && !section.text().isBlank())
                    .map(section -> new ParsedSection(section.pageNumber(), section.item(), section.title(), section.text()))
                    .toList();
            return new FilingSectionsResult(
                    response.filing().accessionNumber(),
                    response.filing().amendsAccessionNumber(),
                    response.filing().form(),
                    parseDate(response.filing().filingDate()),
                    parseDate(response.filing().reportDate()),
                    response.hasSearchableSections(),
                    response.hasSearchableSections() ? sections : List.of());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read filing fixture " + fixture, failure);
        }
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FixtureResponse(
            Filing filing,
            List<Section> sections,
            @JsonProperty("hasSearchableSections") Boolean hasSearchableSections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Filing(
            @JsonProperty("accessionNumber") String accessionNumber,
            @JsonProperty("amendsAccessionNumber") String amendsAccessionNumber,
            String form,
            @JsonProperty("filingDate") String filingDate,
            @JsonProperty("reportDate") String reportDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Section(String item, String title, String text, @JsonProperty("pageNumber") Integer pageNumber) {
    }
}
