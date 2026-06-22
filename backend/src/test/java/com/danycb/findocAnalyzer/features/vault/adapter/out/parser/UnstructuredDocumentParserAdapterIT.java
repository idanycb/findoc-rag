package com.danycb.findocAnalyzer.features.vault.adapter.out.parser;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class UnstructuredDocumentParserAdapterIT {

    private final UnstructuredDocumentParserAdapter parser =
            new UnstructuredDocumentParserAdapter("http://localhost:8000/general/v0/general");

    @Test
    void parsedSections_shouldNotContainNullBytes() throws IOException {
        byte[] pdf;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("Python Mastery.pdf")) {
            assertThat(is).as("Test PDF must be on classpath at src/test/resources/Python Mastery.pdf").isNotNull();
            pdf = is.readAllBytes();
        }

        List<ParsedSection> sections = parser.parse(pdf, "Python Mastery.pdf", "application/pdf");

        assertThat(sections).isNotEmpty();
        for (ParsedSection section : sections) {
            assertThat(section.text())
                    .as("Section on page %d should not contain null bytes", section.pageNumber())
                    .doesNotContain("\0");
        }
    }
}
