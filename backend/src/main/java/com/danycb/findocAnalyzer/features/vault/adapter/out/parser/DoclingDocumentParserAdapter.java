package com.danycb.findocAnalyzer.features.vault.adapter.out.parser;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class DoclingDocumentParserAdapter implements DocumentParserPort {
    private final RestClient restClient;

    public DoclingDocumentParserAdapter(
            @Value("${docling.url}") String doclingUrl,
            @Value("${docling.timeout:PT5M}") Duration timeout) {
        // Converting a real filing on CPU can take tens of seconds, well past the default HTTP
        // read timeout, so the response timeout must be sized for whole-document conversion.
        ReactorClientHttpRequestFactory requestFactory = new ReactorClientHttpRequestFactory();
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(doclingUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<ParsedSection> parse(byte[] content, String fileName, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("files", new ByteArrayResource(content))
                .filename(fileName)
                .contentType(MediaType.parseMediaType(contentType));

        JsonNode response = restClient.post()
                .uri("/v1/convert/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(JsonNode.class);

        List<ParsedSection> sections = mapResponse(response);
        if (sections.isEmpty()) {
            throw new IllegalStateException("No content could be extracted from the document");
        }
        return sections;
    }

    List<ParsedSection> mapResponse(JsonNode response) {
        if (response == null || response.isNull()) {
            return List.of();
        }

        // docling-serve nests the converted document under a "document" envelope, exposing the
        // markdown rendering in "md_content" and the structured DoclingDocument in "json_content".
        JsonNode document = documentNode(response);

        List<ParsedSection> sections = new ArrayList<>();
        addMarkdownSections(document, sections);
        if (sections.isEmpty()) {
            JsonNode structured = document.get("json_content");
            collectTextItems(structured != null && !structured.isNull() ? structured : document, sections, null);
        }
        if (sections.isEmpty()) {
            String text = firstText(document, "text_content", "text", "plain_text", "md_content", "markdown", "md");
            if (!text.isBlank()) {
                sections.add(new ParsedSection(1, null, clean(text)));
            }
        }
        return sections.stream()
                .filter(section -> section.text() != null && !section.text().isBlank())
                .toList();
    }

    private void addMarkdownSections(JsonNode node, List<ParsedSection> sections) {
        String markdown = firstText(node, "md_content", "markdown", "md");
        if (markdown.isBlank()) {
            return;
        }

        String currentTitle = null;
        StringBuilder currentText = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("#")) {
                flushMarkdownSection(sections, currentTitle, currentText);
                currentTitle = line.replaceFirst("^#+\\s*", "").trim();
            }
            currentText.append(line).append('\n');
        }
        flushMarkdownSection(sections, currentTitle, currentText);
    }

    private void flushMarkdownSection(List<ParsedSection> sections, String title, StringBuilder text) {
        String value = clean(text.toString());
        if (!value.isBlank()) {
            sections.add(new ParsedSection(1, blankToNull(title), value));
        }
        text.setLength(0);
    }

    private void collectTextItems(JsonNode node, List<ParsedSection> sections, String activeTitle) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            String text = firstText(node, "text", "orig", "label");
            String type = firstText(node, "type", "label", "self_ref");
            boolean heading = looksLikeHeading(type, node);
            String nextTitle = heading && !text.isBlank() ? text : activeTitle;
            if (!text.isBlank() && !heading) {
                sections.add(new ParsedSection(pageNumber(node), blankToNull(activeTitle), clean(text)));
            }
            for (String field : List.of("texts", "body", "groups", "children", "items", "content", "elements")) {
                JsonNode child = node.get(field);
                if (child != null && !child.isValueNode()) {
                    collectTextItems(child, sections, nextTitle);
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTextItems(child, sections, activeTitle);
            }
        }
    }

    private JsonNode documentNode(JsonNode response) {
        for (String field : List.of("document", "doc", "result", "data")) {
            JsonNode node = response.get(field);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        return response;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private boolean looksLikeHeading(String type, JsonNode node) {
        String normalized = type == null ? "" : type.toLowerCase();
        return normalized.contains("heading") || normalized.contains("title") || normalized.contains("section_header")
                || node.has("level") || node.has("heading_level");
    }

    private int pageNumber(JsonNode node) {
        JsonNode page = node.at("/prov/0/page_no");
        if (!page.isNumber()) {
            page = node.at("/prov/0/pageNumber");
        }
        if (!page.isNumber()) {
            page = node.get("page_number");
        }
        if (!page.isNumber()) {
            page = node.get("page");
        }
        return page != null && page.isNumber() && page.asInt() > 0 ? page.asInt() : 1;
    }

    private String clean(String text) {
        return text == null ? "" : text.replace("\0", "").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
