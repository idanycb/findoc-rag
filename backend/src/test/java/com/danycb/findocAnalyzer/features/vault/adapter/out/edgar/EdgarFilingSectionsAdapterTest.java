package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.danycb.findocAnalyzer.features.vault.application.EdgarServiceUnavailableException;
import com.danycb.findocAnalyzer.features.vault.application.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EdgarFilingSectionsAdapterTest {
    private HttpServer server;
    private EdgarFilingSectionsAdapter adapter;
    private String responseBody;
    private int responseStatus;

    @BeforeEach
    void startServer() throws IOException {
        responseBody = searchableResponse();
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/filings/sections", this::respond);
        server.start();
        adapter = new EdgarFilingSectionsAdapter("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchSectionsMapsFilingMetadataAndStableItemKey() {
        var result = adapter.fetchSections("AAPL", "0000320193-25-000020");

        assertThat(result.accessionNumber()).isEqualTo("0000320193-25-000020");
        assertThat(result.amendsAccessionNumber()).isEqualTo("0000320193-24-000123");
        assertThat(result.formType()).isEqualTo("10-K/A");
        assertThat(result.filingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(result.reportDate()).isEqualTo(LocalDate.of(2024, 9, 28));
        assertThat(result.hasSearchableSections()).isTrue();
        assertThat(result.sections()).singleElement().satisfies(section -> {
            assertThat(section.pageNumber()).isEqualTo(42);
            assertThat(section.item()).isEqualTo("Item 1A");
            assertThat(section.title()).isEqualTo("Risk Factors");
            assertThat(section.text()).isEqualTo("Risk text");
        });
    }

    @Test
    void missingPageProvenanceRemainsUnknown() {
        responseBody = searchableResponse().replace(",\"pageNumber\":42", "");

        var result = adapter.fetchSections("AAPL", "0000320193-25-000020");

        assertThat(result.sections()).singleElement().satisfies(section ->
                assertThat(section.pageNumber()).isNull());
    }

    @Test
    void validAmendmentWithoutSectionsIsSuccessfulAndPreservesRelationship() {
        responseBody = emptyResponse("10-K/A", "0000320193-25-000020", "0000320193-24-000123");

        var result = adapter.fetchSections("AAPL", "0000320193-25-000020");

        assertThat(result.hasSearchableSections()).isFalse();
        assertThat(result.sections()).isEmpty();
        assertThat(result.accessionNumber()).isEqualTo("0000320193-25-000020");
        assertThat(result.amendsAccessionNumber()).isEqualTo("0000320193-24-000123");
    }

    @Test
    void validOriginalWithoutSectionsIsSuccessful() {
        responseBody = emptyResponse("10-Q", "0000320193-25-000030", null);

        var result = adapter.fetchSections("AAPL", "0000320193-25-000030");

        assertThat(result.hasSearchableSections()).isFalse();
        assertThat(result.sections()).isEmpty();
        assertThat(result.amendsAccessionNumber()).isNull();
    }

    @Test
    void searchableFlagWithNoUsableSectionsIsRejected() {
        responseBody = """
                {
                  "company":{"ticker":"AAPL","cik":"0000320193","name":"Apple Inc."},
                  "filing":{"accessionNumber":"0000320193-25-000020","form":"10-K/A",
                            "filingDate":"2025-01-02","reportDate":"2024-09-28",
                            "amendsAccessionNumber":"0000320193-24-000123"},
                  "sourceUrl":"https://sec.example/amendment",
                  "sections":[{"item":"Item 1A","title":"Risk Factors","text":"   "}],
                  "hasSearchableSections":true
                }
                """;

        assertThatThrownBy(() -> adapter.fetchSections("AAPL", "0000320193-25-000020"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("searchable");
    }

    @Test
    void missingSearchabilityFlagIsRejectedAsAnIncompleteContract() {
        responseBody = """
                {
                  "company":{"ticker":"AAPL","cik":"0000320193","name":"Apple Inc."},
                  "filing":{"accessionNumber":"0000320193-25-000020","form":"10-K/A",
                            "amendsAccessionNumber":"0000320193-24-000123"},
                  "sourceUrl":"https://sec.example/amendment",
                  "sections":[]
                }
                """;

        assertThatThrownBy(() -> adapter.fetchSections("AAPL", "0000320193-25-000020"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hasSearchableSections");
    }

    @Test
    void sectionLookupTranslatesSidecarErrorsToStableApplicationExceptions() {
        responseBody = "{\"detail\":\"filing not found\"}";
        responseStatus = 404;
        assertThatThrownBy(() -> adapter.fetchSections("AAPL", "missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("filing not found");

        responseBody = "{\"detail\":\"invalid accession\"}";
        responseStatus = 422;
        assertThatThrownBy(() -> adapter.fetchSections("AAPL", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid accession");

        responseBody = "{\"detail\":\"upstream failed\"}";
        responseStatus = 502;
        assertThatThrownBy(() -> adapter.fetchSections("AAPL", "upstream"))
                .isInstanceOf(EdgarServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void transportFailureIsTranslatedToStableServiceUnavailableException() {
        int unavailablePort = server.getAddress().getPort();
        server.stop(0);
        server = null;
        adapter = new EdgarFilingSectionsAdapter("http://localhost:" + unavailablePort);

        assertThatThrownBy(() -> adapter.fetchSections("AAPL", "0000320193-25-000020"))
                .isInstanceOf(EdgarServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    private String searchableResponse() {
        return """
                {
                  "company":{"ticker":"AAPL","cik":"0000320193","name":"Apple Inc."},
                  "filing":{"accessionNumber":"0000320193-25-000020","form":"10-K/A",
                            "filingDate":"2025-01-02","reportDate":"2024-09-28","fiscalPeriod":"FY",
                            "sourceUrl":"https://sec.example/amendment",
                            "amendsAccessionNumber":"0000320193-24-000123"},
                  "sourceUrl":"https://sec.example/amendment",
                  "sections":[
                    {"item":"Item 1A","title":"Risk Factors","text":"Risk text\\u0000","pageNumber":42},
                    {"item":"Item 7","title":"MD&A","text":"   ","pageNumber":43}
                  ],
                  "hasSearchableSections":true
                }
                """;
    }

    private String emptyResponse(String form, String accession, String amendsAccession) {
        String amends = amendsAccession == null ? "null" : "\"" + amendsAccession + "\"";
        return """
                {
                  "company":{"ticker":"AAPL","cik":"0000320193","name":"Apple Inc."},
                  "filing":{"accessionNumber":"%s","form":"%s","filingDate":"2025-01-02",
                            "reportDate":"2024-09-28","amendsAccessionNumber":%s},
                  "sourceUrl":"https://sec.example/filing",
                  "sections":[],
                  "hasSearchableSections":false
                }
                """.formatted(accession, form, amends);
    }

    private void respond(HttpExchange exchange) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
