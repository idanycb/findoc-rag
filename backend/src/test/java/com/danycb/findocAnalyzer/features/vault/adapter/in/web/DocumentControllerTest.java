package com.danycb.findocAnalyzer.features.vault.adapter.in.web;

import com.danycb.findocAnalyzer.features.vault.application.ResourceNotFoundException;
import com.danycb.findocAnalyzer.features.vault.application.dto.DocumentUploadCommand;
import com.danycb.findocAnalyzer.features.vault.application.dto.UploadResult;
import com.danycb.findocAnalyzer.features.vault.application.in.*;
import com.danycb.findocAnalyzer.features.vault.domain.Document;
import com.danycb.findocAnalyzer.features.vault.domain.DocumentStatus;
import com.danycb.findocAnalyzer.infra.exception.GlobalExceptionHandler;
import com.danycb.findocAnalyzer.infra.security.UserPrincipal;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for {@link DocumentController}: verifies request/response wiring, that the caller's
 * {@code teamId} is threaded into every use case, error-to-status translation via
 * {@link GlobalExceptionHandler}, and that {@code @RequireTeamMember} method security is enforced.
 * Collaborators are hand-written recording fakes; no Mockito.
 */
@WebMvcTest(DocumentController.class)
@Import(DocumentControllerTest.TestConfig.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingListDocuments listDocuments;
    @Autowired
    private RecordingGetDocument getDocument;
    @Autowired
    private RecordingInitiateUpload initiateUpload;
    @Autowired
    private RecordingGenerateViewUrl generateViewUrl;
    @Autowired
    private RecordingDeleteDocument deleteDocument;
    @Autowired
    private RecordingRequestAnalysis requestAnalysis;

    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        listDocuments.reset();
        getDocument.reset();
        initiateUpload.reset();
        generateViewUrl.reset();
        deleteDocument.reset();
        requestAnalysis.reset();
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
    void listDocumentsReturnsTeamSummaries() throws Exception {
        getDocument.result = null;
        listDocuments.result = List.of(document("a.pdf"), document("b.pdf"));

        mockMvc.perform(get("/api/v1/documents").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("a.pdf"))
                .andExpect(jsonPath("$[1].fileName").value("b.pdf"));

        assertThat(listDocuments.teamId).isEqualTo(teamId);
    }

    @Test
    void getDocumentReturnsDetail() throws Exception {
        Document doc = document("detail.pdf");
        getDocument.result = doc;

        mockMvc.perform(get("/api/v1/documents/" + doc.getId()).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("detail.pdf"));

        assertThat(getDocument.id).isEqualTo(doc.getId());
        assertThat(getDocument.teamId).isEqualTo(teamId);
    }

    @Test
    void getMissingDocumentReturns404() throws Exception {
        getDocument.error = new ResourceNotFoundException("no such document");

        mockMvc.perform(get("/api/v1/documents/" + UUID.randomUUID()).with(member()))
                .andExpect(status().isNotFound());
    }

    @Test
    void initiateUploadReturns201WithResult() throws Exception {
        UUID docId = UUID.randomUUID();
        initiateUpload.result = new UploadResult(docId, "upload.pdf", "PENDING", "https://s3/put");

        mockMvc.perform(post("/api/v1/documents")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"upload.pdf\",\"size\":2048,\"type\":\"application/pdf\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3/put"));

        assertThat(initiateUpload.teamId).isEqualTo(teamId);
        assertThat(initiateUpload.command.getFileName()).isEqualTo("upload.pdf");
    }

    @Test
    void initiateUploadRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"\",\"size\":-1,\"type\":\"application/pdf\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getViewUrlReturnsUrl() throws Exception {
        UUID docId = UUID.randomUUID();
        generateViewUrl.result = "https://s3/get";

        mockMvc.perform(get("/api/v1/documents/" + docId + "/view").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewUrl").value("https://s3/get"));

        assertThat(generateViewUrl.id).isEqualTo(docId);
        assertThat(generateViewUrl.teamId).isEqualTo(teamId);
    }

    @Test
    void requestAnalysisReturns202() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/documents/" + docId + "/analyze").with(member()))
                .andExpect(status().isAccepted());

        assertThat(requestAnalysis.id).isEqualTo(docId);
        assertThat(requestAnalysis.teamId).isEqualTo(teamId);
    }

    @Test
    void deleteDocumentReturns204() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/documents/" + docId).with(member()))
                .andExpect(status().isNoContent());

        assertThat(deleteDocument.id).isEqualTo(docId);
        assertThat(deleteDocument.teamId).isEqualTo(teamId);
    }

    @Test
    void superAdminIsNotATeamMemberAndIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/documents")
                        .with(principal(UserRole.SUPER_ADMIN, "ROLE_SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isForbidden());
    }

    private Document document(String fileName) {
        Document doc = Document.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .fileName(fileName)
                .fileSize(1024L)
                .contentType("application/pdf")
                .status(DocumentStatus.COMPLETED)
                .build();
        return doc;
    }

    // ---- fakes ------------------------------------------------------------------------------

    static class RecordingListDocuments implements ListDocumentsUseCase {
        UUID teamId;
        List<Document> result = List.of();

        void reset() {
            teamId = null;
            result = List.of();
        }

        @Override
        public List<Document> execute(UUID teamId) {
            this.teamId = teamId;
            return result;
        }
    }

    static class RecordingGetDocument implements GetDocumentUseCase {
        UUID id;
        UUID teamId;
        Document result;
        RuntimeException error;

        void reset() {
            id = null;
            teamId = null;
            result = null;
            error = null;
        }

        @Override
        public Document execute(UUID id, UUID teamId) {
            this.id = id;
            this.teamId = teamId;
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    static class RecordingInitiateUpload implements InitiateUploadUseCase {
        DocumentUploadCommand command;
        UUID teamId;
        UploadResult result;

        void reset() {
            command = null;
            teamId = null;
            result = null;
        }

        @Override
        public UploadResult execute(DocumentUploadCommand command, UUID teamId) {
            this.command = command;
            this.teamId = teamId;
            return result;
        }
    }

    static class RecordingGenerateViewUrl implements GenerateViewUrlUseCase {
        UUID id;
        UUID teamId;
        String result = "";

        void reset() {
            id = null;
            teamId = null;
            result = "";
        }

        @Override
        public String execute(UUID id, UUID teamId) {
            this.id = id;
            this.teamId = teamId;
            return result;
        }
    }

    static class RecordingDeleteDocument implements DeleteDocumentUseCase {
        UUID id;
        UUID teamId;

        void reset() {
            id = null;
            teamId = null;
        }

        @Override
        public void execute(UUID id, UUID teamId) {
            this.id = id;
            this.teamId = teamId;
        }
    }

    static class RecordingRequestAnalysis implements RequestDocumentAnalysisUseCase {
        UUID id;
        UUID teamId;

        void reset() {
            id = null;
            teamId = null;
        }

        @Override
        public void execute(UUID id, UUID teamId) {
            this.id = id;
            this.teamId = teamId;
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
        RecordingListDocuments listDocuments() {
            return new RecordingListDocuments();
        }

        @Bean
        RecordingGetDocument getDocument() {
            return new RecordingGetDocument();
        }

        @Bean
        RecordingInitiateUpload initiateUpload() {
            return new RecordingInitiateUpload();
        }

        @Bean
        RecordingGenerateViewUrl generateViewUrl() {
            return new RecordingGenerateViewUrl();
        }

        @Bean
        RecordingDeleteDocument deleteDocument() {
            return new RecordingDeleteDocument();
        }

        @Bean
        RecordingRequestAnalysis requestAnalysis() {
            return new RecordingRequestAnalysis();
        }
    }
}
