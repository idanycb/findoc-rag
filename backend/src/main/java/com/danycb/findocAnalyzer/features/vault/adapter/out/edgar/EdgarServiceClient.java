package com.danycb.findocAnalyzer.features.vault.adapter.out.edgar;

import com.danycb.findocAnalyzer.features.vault.application.out.FilingCatalogPort;
import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;
import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;

@Component
public class EdgarServiceClient implements FilingCatalogPort {
    private final RestClient restClient;

    public EdgarServiceClient(@Value("${edgar.url}") String edgarServiceUrl) {
        this.restClient = RestClient.create(edgarServiceUrl);
    }

    @Override
    public List<CompanyResult> searchCompanies(String query) {
        List<CompanyResponse> companies;
        try {
            companies = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/companies")
                            .queryParam("q", query)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException failure) {
            throw EdgarErrorTranslator.translate(failure);
        } catch (ResourceAccessException failure) {
            throw EdgarErrorTranslator.unavailable(failure);
        }
        if (companies == null) {
            return List.of();
        }
        return companies.stream()
                .map(company -> new CompanyResult(company.ticker(), company.cik(), company.name()))
                .toList();
    }

    @Override
    public List<FilingResult> listFilings(String companyId, String formType) {
        List<FilingResponse> filings;
        try {
            filings = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/companies/{id}/filings")
                            .queryParam("form", formType)
                            .build(companyId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException failure) {
            throw EdgarErrorTranslator.translate(failure);
        } catch (ResourceAccessException failure) {
            throw EdgarErrorTranslator.unavailable(failure);
        }
        if (filings == null) {
            return List.of();
        }
        return filings.stream()
                .map(filing -> new FilingResult(
                        requireAccession(filing.accessionNumber()),
                        filing.form(),
                        filing.filingDate(),
                        filing.reportDate(),
                        filing.fiscalPeriod(),
                        filing.sourceUrl(),
                        filing.amendsAccessionNumber()))
                .toList();
    }

    private String requireAccession(String accessionNumber) {
        if (accessionNumber == null || accessionNumber.isBlank()) {
            throw new IllegalStateException("EDGAR sidecar returned a blank accession number");
        }
        return accessionNumber;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyResponse(String ticker, String cik, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FilingResponse(
            @JsonProperty("accessionNumber") String accessionNumber,
            String form,
            @JsonProperty("filingDate") LocalDate filingDate,
            @JsonProperty("reportDate") LocalDate reportDate,
            @JsonProperty("fiscalPeriod") String fiscalPeriod,
            @JsonProperty("sourceUrl") String sourceUrl,
            @JsonProperty("amendsAccessionNumber") String amendsAccessionNumber
    ) {
    }
}
