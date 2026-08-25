package com.danycb.findocAnalyzer.features.chat.adapter.in.web;

import com.danycb.findocAnalyzer.features.chat.application.AiAnalysisException;
import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import com.danycb.findocAnalyzer.features.chat.application.dto.AnswerResult;
import com.danycb.findocAnalyzer.features.chat.domain.Citation;
import com.danycb.findocAnalyzer.features.identity.domain.UserRole;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test for {@link ChatController}: verifies the question/answer wiring, that the caller's
 * {@code teamId} is passed to the use case, request validation, the teamless-caller guard, and
 * error-to-status translation. A hand-written recording fake stands in for the use case.
 */
@WebMvcTest(ChatController.class)
@Import(ChatControllerTest.TestConfig.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecordingAnswerQuestion answerQuestion;

    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        answerQuestion.reset();
    }

    private RequestPostProcessor member(UUID teamId) {
        UserPrincipal user = new UserPrincipal("caller", UUID.randomUUID(), UserRole.MEMBER, teamId);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @Test
    void answersQuestionForTeam() throws Exception {
        answerQuestion.result = new AnswerResult("Revenue grew 8%.", List.of(new Citation(
                "0000320193-25-000020", "10-Q/A", LocalDate.of(2025, 5, 2),
                "Part I Item 2", "Management's Discussion and Analysis", 7, "Revenue grew.")));

        mockMvc.perform(post("/api/v1/chat")
                        .with(member(teamId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How did revenue change?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Revenue grew 8%."))
                .andExpect(jsonPath("$.citations[0].accessionNumber").value("0000320193-25-000020"))
                .andExpect(jsonPath("$.citations[0].formType").value("10-Q/A"))
                .andExpect(jsonPath("$.citations[0].sectionItem").value("Part I Item 2"));

        assertThat(answerQuestion.question).isEqualTo("How did revenue change?");
        assertThat(answerQuestion.teamId).isEqualTo(teamId);
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .with(member(teamId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forbidsTeamlessCaller() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .with(member(null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"anything\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mapsAiFailureToBadGateway() throws Exception {
        answerQuestion.error = new AiAnalysisException("upstream LLM unavailable", new RuntimeException("boom"));

        mockMvc.perform(post("/api/v1/chat")
                        .with(member(teamId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"anything\"}"))
                .andExpect(status().isBadGateway());
    }

    // ---- fake -------------------------------------------------------------------------------

    static class RecordingAnswerQuestion implements AnswerQuestionUseCase {
        String question;
        UUID teamId;
        AnswerResult result = new AnswerResult("", List.of());
        RuntimeException error;

        void reset() {
            question = null;
            teamId = null;
            result = new AnswerResult("", List.of());
            error = null;
        }

        @Override
        public AnswerResult execute(String question, UUID teamId) {
            this.question = question;
            this.teamId = teamId;
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    @TestConfiguration
    @EnableWebSecurity
    static class TestConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        @Bean
        RecordingAnswerQuestion answerQuestion() {
            return new RecordingAnswerQuestion();
        }
    }
}
