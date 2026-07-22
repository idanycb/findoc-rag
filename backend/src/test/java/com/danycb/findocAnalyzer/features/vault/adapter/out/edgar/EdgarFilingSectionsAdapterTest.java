package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EdgarFilingSectionsAdapterTest {
    private HttpServer server;
    private EdgarFilingSectionsAdapter adapter;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/filings/sections", exchange -> {
            byte[] body = """
                    {
                      "company": {"ticker":"AAPL","cik":"0000320193","name":"Apple Inc."},
                      "filing": {"accessionNumber":"0000320193-24-000123","form":"10-K"},
                      "sourceUrl": "https://sec.example/aapl",
                      "sections": [
                        {"item":"Item 1A","title":"Risk Factors","text":"Risk text\\u0000","pageNumber": 42},
                        {"item":"Item 7","title":"MD&A","text":"   ","pageNumber": 43}
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        adapter = new EdgarFilingSectionsAdapter("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchSections_mapsWrappedSidecarResponse() {
        var sections = adapter.fetchSections("AAPL", "0000320193-24-000123");

        assertThat(sections).singleElement().satisfies(section -> {
            assertThat(section.pageNumber()).isEqualTo(42);
            assertThat(section.title()).isEqualTo("Risk Factors");
            assertThat(section.text()).isEqualTo("Risk text");
        });
    }
}
