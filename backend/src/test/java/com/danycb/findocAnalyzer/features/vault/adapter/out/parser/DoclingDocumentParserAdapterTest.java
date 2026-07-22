package com.danycb.findocAnalyzer.features.vault.adapter.out.parser;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the response-mapping logic of {@link DoclingDocumentParserAdapter}.
 *
 * <p>These exercise {@code mapResponse} directly against JSON payloads that mirror the real
 * {@code docling-serve} {@code /v1/convert/file} contract (v1.21.0): the converted document is
 * nested under a {@code document} envelope, with markdown in {@code document.md_content} and the
 * structured {@code DoclingDocument} in {@code document.json_content}. No network is involved.
 */
class DoclingDocumentParserAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DoclingDocumentParserAdapter adapter =
            new DoclingDocumentParserAdapter("http://docling.invalid", Duration.ofSeconds(1));

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }

    @Test
    void extractsSectionsFromMarkdownEnvelope() {
        // The default docling-serve response: only document.md_content is populated.
        JsonNode response = json("""
                {
                  "document": {
                    "filename": "10k.pdf",
                    "md_content": "## Item 1. Business\\n\\nWe design and sell products.\\n\\n## Item 1A. Risk Factors\\n\\nOur business faces many risks.",
                    "json_content": null,
                    "html_content": null,
                    "text_content": null,
                    "doctags_content": null
                  },
                  "status": "success",
                  "errors": []
                }
                """);

        List<ParsedSection> sections = adapter.mapResponse(response);

        assertThat(sections).hasSize(2);
        assertThat(sections).extracting(ParsedSection::title)
                .containsExactly("Item 1. Business", "Item 1A. Risk Factors");
        assertThat(sections.get(0).text()).contains("We design and sell products.");
        assertThat(sections.get(1).text()).contains("Our business faces many risks.");
    }

    @Test
    void singleHeadingMarkdownProducesOneSection() {
        JsonNode response = json("""
                {"document": {"md_content": "## Hello Docling Section One"}, "status": "success"}
                """);

        List<ParsedSection> sections = adapter.mapResponse(response);

        assertThat(sections).singleElement().satisfies(section -> {
            assertThat(section.title()).isEqualTo("Hello Docling Section One");
            assertThat(section.text()).contains("Hello Docling Section One");
        });
    }

    @Test
    void fallsBackToStructuredJsonContentWhenMarkdownAbsent() {
        // docling-serve populates document.json_content (a DoclingDocument) when JSON output is
        // requested. Text lives in a flat "texts" array; page numbers come from prov[0].page_no.
        JsonNode response = json("""
                {
                  "document": {
                    "md_content": null,
                    "json_content": {
                      "schema_name": "DoclingDocument",
                      "texts": [
                        {"self_ref": "#/texts/0", "label": "section_header", "text": "Item 1. Business", "level": 1, "prov": [{"page_no": 3}]},
                        {"self_ref": "#/texts/1", "label": "text", "text": "We design and sell products.", "prov": [{"page_no": 3}]},
                        {"self_ref": "#/texts/2", "label": "text", "text": "Revenue grew year over year.", "prov": [{"page_no": 4}]}
                      ],
                      "body": {"self_ref": "#/body", "children": [{"$ref": "#/texts/0"}, {"$ref": "#/texts/1"}, {"$ref": "#/texts/2"}]}
                    }
                  },
                  "status": "success"
                }
                """);

        List<ParsedSection> sections = adapter.mapResponse(response);

        // Headings are consumed as titles, not emitted as their own body sections.
        assertThat(sections).extracting(ParsedSection::text)
                .containsExactly("We design and sell products.", "Revenue grew year over year.");
        assertThat(sections).extracting(ParsedSection::pageNumber)
                .containsExactly(3, 4);
    }

    @Test
    void stripsNullBytesFromExtractedText() {
        JsonNode response = json("""
                {"document": {"md_content": "## Clean\\n\\nText with a \\u0000 null byte."}, "status": "success"}
                """);

        List<ParsedSection> sections = adapter.mapResponse(response);

        assertThat(sections).isNotEmpty();
        assertThat(sections).allSatisfy(section ->
                assertThat(section.text()).doesNotContain("\0"));
    }

    @Test
    void returnsEmptyForNullResponse() {
        assertThat(adapter.mapResponse(null)).isEmpty();
        assertThat(adapter.mapResponse(json("null"))).isEmpty();
    }

    @Test
    void returnsEmptyWhenDocumentHasNoExtractableContent() {
        JsonNode response = json("""
                {"document": {"md_content": null, "json_content": null, "text_content": null}, "status": "success"}
                """);

        assertThat(adapter.mapResponse(response)).isEmpty();
    }

    @Test
    void dropsBlankSections() {
        JsonNode response = json("""
                {"document": {"md_content": "## Heading\\n\\n   \\n\\n## Real\\n\\nBody."}, "status": "success"}
                """);

        List<ParsedSection> sections = adapter.mapResponse(response);

        assertThat(sections).allSatisfy(section ->
                assertThat(section.text()).isNotBlank());
    }
}
