package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureFilingSectionsAdapterTest {
    @TempDir
    Path corpus;

    @Test
    void readsVerbatimSidecarFixtureWithoutInventingPageProvenance() throws Exception {
        Files.writeString(corpus.resolve("accession.json"), """
                {"filing":{"accessionNumber":"accession","amendsAccessionNumber":"original",
                  "form":"10-K/A"},
                 "sections":[{"item":"Explanatory Note","title":"Explanatory Note","text":"Purpose text"}],
                 "hasSearchableSections":true}
                """);
        ObjectMapper mapper = new ObjectMapper();
        var adapter = new FixtureFilingSectionsAdapter(mapper, corpus);

        var result = adapter.fetchSections("TSLA", "accession");

        assertThat(result.amendsAccessionNumber()).isEqualTo("original");
        assertThat(result.sections()).singleElement().satisfies(section -> {
            assertThat(section.item()).isEqualTo("Explanatory Note");
            assertThat(section.pageNumber()).isNull();
        });
    }
}
