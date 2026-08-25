package com.danycb.findocAnalyzer.features.vault.adapter.in.web;

import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.ImportFilingResult;
import com.danycb.findocAnalyzer.features.vault.application.EdgarServiceUnavailableException;
import com.danycb.findocAnalyzer.features.vault.application.ResourceNotFoundException;
import com.danycb.findocAnalyzer.features.vault.application.in.ImportFilingUseCase;
import com.danycb.findocAnalyzer.features.vault.application.in.ListFilingsUseCase;
import com.danycb.findocAnalyzer.features.vault.application.in.SearchCompaniesUseCase;
import com.danycb.findocAnalyzer.features.vault.domain.CompanyResult;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.features.vault.domain.FilingResult;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for {@link EdgarController}: verifies SEC company/filing lookup wiring, filing
 * import (with the caller's {@code teamId}), request validation, and that {@code @RequireTeamMember}
 * method security is enforced. Collaborators are hand-written recording fakes.
 */
@WebMvcTest(EdgarController.class)
@Import(EdgarControllerTest.TestConfig.class)
class EdgarControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecordingSearchCompanies searchCompanies;
    @Autowired
    private RecordingListFilings listFilings;
    @Autowired
    private RecordingImportFiling importFiling;

    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        searchCompanies.reset();
        listFilings.reset();
        importFiling.reset();
    }

    private RequestPostProcessor member() {
        return principal(UserRole.MEMBER, "ROLE_MEMBER", teamId);
    }

    private static RequestPostProcessor principal(UserRole role, String authority, UUID teamId) {
        UserPrincipal user = new UserPrincipal("caller", UUID.randomUUID(), role, teamId);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Test
    void searchCompaniesReturnsMatches() throws Exception {
        searchCompanies.result = List.of(new CompanyResult("AAPL", "320193", "Apple Inc."));

        mockMvc.perform(get("/api/v1/edgar/companies").param("q", "apple").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."));

        assertThat(searchCompanies.query).isEqualTo("apple");
    }

    @Test
    void listFilingsPassesCompanyIdAndType() throws Exception {
        listFilings.result = List.of(new FilingResult(
                "0000320193-24-000123", "10-K",
                LocalDate.of(2024, 11, 1), LocalDate.of(2024, 9, 28), "FY2024", "https://sec.example/aapl", null));

        mockMvc.perform(get("/api/v1/edgar/companies/320193/filings").param("type", "10-K").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].form").value("10-K"));

        assertThat(listFilings.companyId).isEqualTo("320193");
        assertThat(listFilings.formType).isEqualTo("10-K");
    }

    @Test
    void listFilingsReturnsAmendmentRelationship() throws Exception {
        listFilings.result = List.of(new FilingResult(
                "0000320193-25-000020", "10-K/A",
                LocalDate.of(2025, 1, 2), LocalDate.of(2024, 9, 28), "FY",
                "https://sec.example/amendment", "0000320193-24-000123"));

        mockMvc.perform(get("/api/v1/edgar/companies/AAPL/filings").param("type", "10-K/A").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accessionNumber").value("0000320193-25-000020"))
                .andExpect(jsonPath("$[0].amendsAccessionNumber").value("0000320193-24-000123"));
    }

    @Test
    void importFilingReturns202WithStatus() throws Exception {
        UUID docId = UUID.randomUUID();
        importFiling.result = new ImportFilingResult(docId, "aapl-10k.pdf", DocumentStatus.PENDING);

        mockMvc.perform(post("/api/v1/edgar/filings/import")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"AAPL","accessionNumber":"0000320193-25-000020",
                                 "amendsAccessionNumber":"0000320193-24-000123","cik":"320193",
                                 "companyName":"Apple Inc.","formType":"10-K/A","fiscalPeriod":"FY2024",
                                 "reportDate":"2024-09-28","filingDate":"2024-11-01",
                                 "sourceUrl":"https://sec.example/aapl"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(importFiling.teamId).isEqualTo(teamId);
        assertThat(importFiling.command.accessionNumber()).isEqualTo("0000320193-25-000020");
        assertThat(importFiling.command.amendsAccessionNumber()).isEqualTo("0000320193-24-000123");
    }

    @Test
    void importFilingRejectsBlankTicker() throws Exception {
        mockMvc.perform(post("/api/v1/edgar/filings/import")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"\",\"accessionNumber\":\"0000320193-24-000123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importFilingRejectsUnsupportedForm() throws Exception {
        mockMvc.perform(post("/api/v1/edgar/filings/import")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"AAPL","accessionNumber":"0000320193-24-000123",
                                 "formType":"8-K"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filingLookupMapsNotFoundTo404() throws Exception {
        listFilings.error = new ResourceNotFoundException("filing not found");

        mockMvc.perform(get("/api/v1/edgar/companies/AAPL/filings").param("type", "10-K").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("filing not found"));
    }

    @Test
    void filingLookupMapsInvalidSidecarRequestTo400() throws Exception {
        listFilings.error = new IllegalArgumentException("unsupported form");

        mockMvc.perform(get("/api/v1/edgar/companies/AAPL/filings").param("type", "8-K").with(member()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported form"));
    }

    @Test
    void filingLookupMapsSidecarOutageTo502() throws Exception {
        listFilings.error = new EdgarServiceUnavailableException("EDGAR temporarily unavailable");

        mockMvc.perform(get("/api/v1/edgar/companies/AAPL/filings").param("type", "10-K").with(member()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("EDGAR temporarily unavailable"));
    }

    @Test
    void superAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/edgar/companies").param("q", "apple")
                        .with(principal(UserRole.SUPER_ADMIN, "ROLE_SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/edgar/companies").param("q", "apple"))
                .andExpect(status().isForbidden());
    }

    // ---- fakes ------------------------------------------------------------------------------

    static class RecordingSearchCompanies implements SearchCompaniesUseCase {
        String query;
        List<CompanyResult> result = List.of();

        void reset() {
            query = null;
            result = List.of();
        }

        @Override
        public List<CompanyResult> search(String query) {
            this.query = query;
            return result;
        }
    }

    static class RecordingListFilings implements ListFilingsUseCase {
        String companyId;
        String formType;
        List<FilingResult> result = List.of();
        RuntimeException error;

        void reset() {
            companyId = null;
            formType = null;
            result = List.of();
            error = null;
        }

        @Override
        public List<FilingResult> list(String companyId, String formType) {
            this.companyId = companyId;
            this.formType = formType;
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    static class RecordingImportFiling implements ImportFilingUseCase {
        ImportFilingCommand command;
        UUID teamId;
        ImportFilingResult result;

        void reset() {
            command = null;
            teamId = null;
            result = null;
        }

        @Override
        public ImportFilingResult importFiling(ImportFilingCommand command, UUID teamId) {
            this.command = command;
            this.teamId = teamId;
            return result;
        }
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        @Bean
        RecordingSearchCompanies searchCompanies() {
            return new RecordingSearchCompanies();
        }

        @Bean
        RecordingListFilings listFilings() {
            return new RecordingListFilings();
        }

        @Bean
        RecordingImportFiling importFiling() {
            return new RecordingImportFiling();
        }
    }
}
