package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EdgarServiceClientTest {
    private HttpServer server;
    private EdgarServiceClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/companies", exchange -> {
            byte[] body;
            if (exchange.getRequestURI().getPath().endsWith("/filings")) {
                body = """
                        [{"accessionNumber":"0000320193-24-000123","form":"10-K","filingDate":"2024-11-01","reportDate":"2024-09-28","fiscalPeriod":"FY2024","sourceUrl":"https://sec.example/aapl"}]
                        """.getBytes(StandardCharsets.UTF_8);
            } else {
                body = """
                        [{"ticker":"AAPL","cik":"320193","name":"Apple Inc."}]
                        """.getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new EdgarServiceClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void searchCompanies_mapsSidecarResponse() {
        var companies = client.searchCompanies("apple");

        assertThat(companies).singleElement().satisfies(company -> {
            assertThat(company.ticker()).isEqualTo("AAPL");
            assertThat(company.cik()).isEqualTo("320193");
            assertThat(company.name()).isEqualTo("Apple Inc.");
        });
    }

    @Test
    void listFilings_mapsSidecarResponse() {
        var filings = client.listFilings("AAPL", "10-K");

        assertThat(filings).singleElement().satisfies(filing -> {
            assertThat(filing.accessionNumber()).isEqualTo("0000320193-24-000123");
            assertThat(filing.form()).isEqualTo("10-K");
            assertThat(filing.fiscalPeriod()).isEqualTo("FY2024");
            assertThat(filing.sourceUrl()).isEqualTo("https://sec.example/aapl");
        });
    }
}
