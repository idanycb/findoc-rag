package com.danycb.findocAnalyzer.features.vault.adapter.out.parser;

import com.danycb.findocAnalyzer.features.vault.domain.ParsedSection;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DoclingDocumentParserAdapter}: the full HTTP round-trip
 * (multipart upload plus JSON deserialization and mapping) against a stub {@code docling-serve}
 * backed by the JDK {@link HttpServer}. Self-contained — needs no running docling instance.
 */
class DoclingDocumentParserAdapterIT {

    private HttpServer server;
    private DoclingDocumentParserAdapter adapter;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicInteger lastBodyLength = new AtomicInteger();
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/convert/file", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            lastBodyLength.set(requestBody.length);

            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        adapter = new DoclingDocumentParserAdapter(
                "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(10));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsMultipartToConvertEndpointAndMapsResponse() {
        responseBody.set("""
                {
                  "document": {
                    "filename": "10k.pdf",
                    "md_content": "## Item 1. Business\\n\\nWe design and sell products."
                  },
                  "status": "success"
                }
                """);

        byte[] pdf = "%PDF-1.4 fake bytes".getBytes(StandardCharsets.UTF_8);
        List<ParsedSection> sections = adapter.parse(pdf, "10k.pdf", "application/pdf");

        assertThat(lastPath.get()).isEqualTo("/v1/convert/file");
        assertThat(lastContentType.get()).startsWith("multipart/form-data");
        assertThat(lastBodyLength.get()).isGreaterThan(pdf.length);

        assertThat(sections).singleElement().satisfies(section -> {
            assertThat(section.title()).isEqualTo("Item 1. Business");
            assertThat(section.text()).contains("We design and sell products.");
        });
    }

    @Test
    void throwsWhenDocumentYieldsNoContent() {
        responseBody.set("""
                {"document": {"md_content": null, "json_content": null}, "status": "success"}
                """);

        assertThatThrownBy(() -> adapter.parse(new byte[]{1, 2, 3}, "empty.pdf", "application/pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No content");
    }

    @Test
    void propagatesServerErrors() {
        responseStatus.set(500);
        responseBody.set("{\"detail\": \"conversion failed\"}");

        assertThatThrownBy(() -> adapter.parse(new byte[]{1, 2, 3}, "boom.pdf", "application/pdf"))
                .isInstanceOf(RuntimeException.class);
    }
}
