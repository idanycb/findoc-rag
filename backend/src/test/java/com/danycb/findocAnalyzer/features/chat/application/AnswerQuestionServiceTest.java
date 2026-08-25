package com.danycb.findocAnalyzer.features.chat.application;

import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerQuestionServiceTest {

    @Test
    void answerCarriesExactAmendmentCitationAndUsesItInContext() {
        RecordingLlm llm = new RecordingLlm();
        AnswerQuestionService service = new AnswerQuestionService(
                llm,
                (query, teamId) -> List.of(amendmentChunk("risk text")));

        var result = service.execute("What are the risks?", UUID.randomUUID());

        assertThat(result.answer()).isEqualTo("answer");
        assertThat(llm.context).contains(
                "[AAPL 10-K/A FY2024 - Item 1A Risk Factors, Pg 12, accession 0000320193-25-000020] risk text");
        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.accessionNumber()).isEqualTo("0000320193-25-000020");
            assertThat(citation.formType()).isEqualTo("10-K/A");
            assertThat(citation.filingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
            assertThat(citation.sectionItem()).isEqualTo("Item 1A");
            assertThat(citation.title()).isEqualTo("Risk Factors");
            assertThat(citation.page()).isEqualTo(12);
        });
    }

    @Test
    void uploadCitationOmitsAbsentEdgarProvenanceGracefully() {
        RecordingLlm llm = new RecordingLlm();
        VectorSearchPort search = (query, teamId) -> List.of(new RetrievedChunk(
                "e1", "upload.pdf", null, 3, "upload text", null, null, null, null));
        AnswerQuestionService service = new AnswerQuestionService(llm, search);

        var result = service.execute("Summarize", UUID.randomUUID());

        assertThat(llm.context).contains("[upload.pdf, Pg 3] upload text");
        assertThat(result.citations()).singleElement().satisfies(citation ->
                assertThat(citation.accessionNumber()).isNull());
    }

    @Test
    void citationsComeFromTheSuccessfulRetryRatherThanAnEarlierUnsupportedAttempt() {
        RecordingLlm llm = new RecordingLlm();
        llm.answers = new java.util.ArrayDeque<>(List.of(
                "The current document vault does not contain information to answer this question.",
                "answer"));
        VectorSearchPort search = (query, teamId) -> query.equals("rewritten")
                ? List.of(amendmentChunk("amended evidence"))
                : List.of(new RetrievedChunk("old", "old.pdf", "Old", 1, "old evidence",
                "old-accession", "10-K", LocalDate.of(2024, 1, 1), "Item 1"));
        AnswerQuestionService service = new AnswerQuestionService(llm, search);

        var result = service.execute("question", UUID.randomUUID());

        assertThat(result.citations()).extracting(citation -> citation.accessionNumber())
                .containsExactly("0000320193-25-000020");
    }

    private RetrievedChunk amendmentChunk(String text) {
        return new RetrievedChunk(
                "e1",
                "AAPL 10-K/A FY2024",
                "Risk Factors",
                12,
                text,
                "0000320193-25-000020",
                "10-K/A",
                LocalDate.of(2025, 1, 2),
                "Item 1A");
    }

    static class RecordingLlm implements LlmPort {
        String context;
        java.util.ArrayDeque<String> answers = new java.util.ArrayDeque<>(List.of("answer"));

        @Override
        public String rewriteForSearch(String question) {
            return "rewritten";
        }

        @Override
        public String generateHypotheticalAnswer(String question) {
            return question;
        }

        @Override
        public String answerWithContext(String context, String question) {
            this.context = context;
            return answers.removeFirst();
        }
    }
}
