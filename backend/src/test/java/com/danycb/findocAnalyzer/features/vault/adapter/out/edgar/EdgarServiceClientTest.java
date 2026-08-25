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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EdgarServiceClientTest {
    private HttpServer server;
    private EdgarServiceClient client;
    private String lastQuery;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/companies", this::handleCompanies);
        server.start();
        client = new EdgarServiceClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchCompaniesMapsSidecarResponse() {
        var companies = client.searchCompanies("apple");

        assertThat(companies).singleElement().satisfies(company -> {
            assertThat(company.ticker()).isEqualTo("AAPL");
            assertThat(company.cik()).isEqualTo("320193");
            assertThat(company.name()).isEqualTo("Apple Inc.");
        });
    }

    @Test
    void listFilingsMapsNullableOriginalAccession() {
        var filings = client.listFilings("AAPL", "10-K/A");

        assertThat(filings).hasSize(2);
        assertThat(filings.get(0).accessionNumber()).isEqualTo("0000320193-25-000020");
        assertThat(filings.get(0).form()).isEqualTo("10-K/A");
        assertThat(filings.get(0).amendsAccessionNumber()).isEqualTo("0000320193-24-000123");
        assertThat(filings.get(1).accessionNumber()).isEqualTo("0000320193-25-000021");
        assertThat(filings.get(1).amendsAccessionNumber()).isNull();
    }

    @Test
    void listFilingsPreservesExactAmendmentFormInRequest() {
        client.listFilings("AAPL", "10-Q/A");

        assertThat(lastQuery).contains("form=10-Q/A");
    }

    @Test
    void listFilingsTranslatesSidecarErrorsToStableApplicationExceptions() {
        assertThatThrownBy(() -> client.listFilings("AAPL", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported form");
        assertThatThrownBy(() -> client.listFilings("AAPL", "MISSING"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("company not found");
        assertThatThrownBy(() -> client.listFilings("AAPL", "UPSTREAM"))
                .isInstanceOf(EdgarServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void listFilingsRejectsMalformedBlankAccessionDefensively() {
        assertThatThrownBy(() -> client.listFilings("AAPL", "BLANK"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accession");
    }

    @Test
    void transportFailureIsTranslatedToStableServiceUnavailableException() {
        int unavailablePort = server.getAddress().getPort();
        server.stop(0);
        server = null;
        client = new EdgarServiceClient("http://localhost:" + unavailablePort);

        assertThatThrownBy(() -> client.listFilings("AAPL", "10-K"))
                .isInstanceOf(EdgarServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    private void handleCompanies(HttpExchange exchange) throws IOException {
        String decodedQuery = URLDecoder.decode(
                exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery(),
                StandardCharsets.UTF_8);
        lastQuery = decodedQuery;

        if (!exchange.getRequestURI().getPath().endsWith("/filings")) {
            respond(exchange, 200, """
                    [{"ticker":"AAPL","cik":"320193","name":"Apple Inc."}]
                    """);
            return;
        }
        if (decodedQuery.contains("form=INVALID")) {
            respond(exchange, 422, "{\"detail\":\"unsupported form\"}");
            return;
        }
        if (decodedQuery.contains("form=MISSING")) {
            respond(exchange, 404, "{\"detail\":\"company not found\"}");
            return;
        }
        if (decodedQuery.contains("form=UPSTREAM")) {
            respond(exchange, 502, "{\"detail\":\"upstream failed\"}");
            return;
        }
        if (decodedQuery.contains("form=BLANK")) {
            respond(exchange, 200, """
                    [{"accessionNumber":" ","form":"10-K","filingDate":"2024-11-01"}]
                    """);
            return;
        }
        if (decodedQuery.contains("form=10-K/A")) {
            respond(exchange, 200, """
                    [
                      {"accessionNumber":"0000320193-25-000020","form":"10-K/A",
                       "filingDate":"2025-01-02","reportDate":"2024-09-28","fiscalPeriod":"FY",
                       "sourceUrl":"https://sec.example/amendment-1",
                       "amendsAccessionNumber":"0000320193-24-000123"},
                      {"accessionNumber":"0000320193-25-000021","form":"10-K/A",
                       "filingDate":"2025-01-03","reportDate":"2024-09-28","fiscalPeriod":"FY",
                       "sourceUrl":"https://sec.example/unmatched-amendment",
                       "amendsAccessionNumber":null}
                    ]
                    """);
            return;
        }
        respond(exchange, 200, "[]");
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
