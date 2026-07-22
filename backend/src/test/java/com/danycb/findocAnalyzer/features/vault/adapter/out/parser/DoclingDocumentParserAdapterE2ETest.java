package com.danycb.findocAnalyzer.features.vault.adapter.out.parser;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests that drive a real {@code docling-serve} instance (docker-compose service
 * {@code docling-serve}, default {@code http://localhost:5001}).
 *
 * <p>Opt-in only: run with {@code -Ddocling.e2e=true}, optionally overriding the endpoint via
 * {@code -Ddocling.url=...}. Skipped by default so the suite never depends on an external service.
 */
@Tag("e2e")
@EnabledIfSystemProperty(named = "docling.e2e", matches = "true")
class DoclingDocumentParserAdapterE2ETest {

    private final DoclingDocumentParserAdapter parser =
            new DoclingDocumentParserAdapter(
                    System.getProperty("docling.url", "http://localhost:5001"), Duration.ofMinutes(5));

    @Test
    void parsesRealPdfIntoNonEmptySections() throws IOException {
        byte[] pdf = loadFixture("Python Mastery.pdf");

        List<ParsedSection> sections = parser.parse(pdf, "Python Mastery.pdf", "application/pdf");

        assertThat(sections).isNotEmpty();
        assertThat(sections).allSatisfy(section -> {
            assertThat(section.text()).isNotBlank();
            assertThat(section.text())
                    .as("Section on page %d must not contain null bytes", section.pageNumber())
                    .doesNotContain("\0");
            assertThat(section.pageNumber()).isPositive();
        });
    }

    private byte[] loadFixture(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(is).as("Test fixture must be on classpath at src/test/resources/%s", name).isNotNull();
            return is.readAllBytes();
        }
    }
}
