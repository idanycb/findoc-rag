package com.danycb.findocAnalyzer.features.vault.adapter.out.parser;

import com.danycb.findocAnalyzer.features.vault.application.out.DocumentParserPort;
import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class UnstructuredDocumentParserAdapter implements DocumentParserPort {

    private final RestClient restClient;

    public UnstructuredDocumentParserAdapter(
            @Value("${unstructured.url:http://localhost:8000/general/v0/general}") String unstructuredUrl) {
        this.restClient = RestClient.create(unstructuredUrl);
    }

    @Override
    public List<ParsedSection> parse(byte[] content, String fileName, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("files", new ByteArrayResource(content))
                .filename(fileName)
                .contentType(MediaType.parseMediaType(contentType));
        builder.part("strategy", "fast");
        builder.part("chunking_strategy", "by_title");

        List<UnstructuredResponse> elements = restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (elements == null || elements.isEmpty()) {
            throw new RuntimeException("No content could be extracted from the document");
        }

        return elements.stream()
                .filter(e -> e.getText() != null && !e.getText().isBlank())
                .map(e -> new ParsedSection(e.getPageNumber(), e.getText().replace("\0", "")))
                .toList();
    }
}
