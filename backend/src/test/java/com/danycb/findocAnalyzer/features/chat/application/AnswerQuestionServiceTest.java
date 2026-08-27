package com.danycb.findocAnalyzer.features.chat.application;

import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.ClaimCitation;
import com.danycb.findocAnalyzer.features.chat.domain.GroundedAnswer;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievalOutcome;
import com.danycb.findocAnalyzer.features.chat.domain.AttemptType;
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
                (query, teamId) -> RetrievalOutcome.selectedOnly(List.of(amendmentChunk("risk text"))));

        var result = service.execute("What are the risks?", UUID.randomUUID());

        assertThat(result.answer()).isEqualTo("answer");
        assertThat(llm.context)
                .contains("[S1; AAPL 10-K/A FY2024 - Item 1A Risk Factors, Pg 12, accession 0000320193-25-000020] risk text")
                .doesNotContain("e1");
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
        VectorSearchPort search = (query, teamId) -> RetrievalOutcome.selectedOnly(List.of(new RetrievedChunk(
                "e1", "upload.pdf", null, 3, "upload text", null, null, null, null)));
        AnswerQuestionService service = new AnswerQuestionService(llm, search);

        var result = service.execute("Summarize", UUID.randomUUID());

        assertThat(llm.context).contains("[S1; upload.pdf, Pg 3] upload text");
        assertThat(result.citations()).singleElement().satisfies(citation ->
                assertThat(citation.accessionNumber()).isNull());
    }

    @Test
    void unknownPageProvenanceDoesNotInventPageLabel() {
        RecordingLlm llm = new RecordingLlm();
        RetrievedChunk chunk = new RetrievedChunk(
                "e1", "TSLA 10-K/A", "Explanatory Note", null, "amendment purpose",
                "accession", "10-K/A", LocalDate.of(2025, 4, 30), "Explanatory Note");
        AnswerQuestionService service = new AnswerQuestionService(
                llm, (query, teamId) -> RetrievalOutcome.selectedOnly(List.of(chunk)));

        service.execute("What was amended?", UUID.randomUUID());

        assertThat(llm.context)
                .contains("[S1; TSLA 10-K/A - Explanatory Note, accession accession]")
                .doesNotContain("Pg");
    }

    @Test
    void citationsComeFromTheSuccessfulRetryRatherThanAnEarlierUnsupportedAttempt() {
        RecordingLlm llm = new RecordingLlm();
        llm.answers = new java.util.ArrayDeque<>(List.of(
                new GroundedAnswer(false, "The provided documents do not specify this.", List.of()),
                groundedAnswer("S1")));
        VectorSearchPort search = (query, teamId) -> RetrievalOutcome.selectedOnly(query.equals("rewritten")
                ? List.of(amendmentChunk("amended evidence"))
                : List.of(new RetrievedChunk("old", "old.pdf", "Old", 1, "old evidence",
                "old-accession", "10-K", LocalDate.of(2024, 1, 1), "Item 1")));
        AnswerQuestionService service = new AnswerQuestionService(llm, search);

        var result = service.execute("question", UUID.randomUUID());

        assertThat(result.citations()).extracting(citation -> citation.accessionNumber())
                .containsExactly("0000320193-25-000020");
        assertThat(result.attempts()).extracting(attempt -> attempt.type())
                .containsExactly(AttemptType.ORIGINAL, AttemptType.REWRITE);
    }

    @Test
    void answerableResultDoesNotRetryEvenWhenAnswerContainsLegacyMarker() {
        RecordingLlm llm = new RecordingLlm();
        llm.answers = new java.util.ArrayDeque<>(List.of(new GroundedAnswer(
                true,
                "This discusses the phrase: The current document vault does not contain information to answer this question.",
                List.of(new ClaimCitation("S1")))));
        AnswerQuestionService service = new AnswerQuestionService(
                llm,
                (query, teamId) -> RetrievalOutcome.selectedOnly(List.of(amendmentChunk("risk text"))));

        var result = service.execute("question", UUID.randomUUID());

        assertThat(result.attempts()).extracting(attempt -> attempt.type())
                .containsExactly(AttemptType.ORIGINAL);
    }

    @Test
    void citationsIncludeOnlyExplicitlyCitedRequestSourceIds() {
        RecordingLlm llm = new RecordingLlm();
        llm.answers = new java.util.ArrayDeque<>(List.of(new GroundedAnswer(
                true,
                "answer",
                List.of(new ClaimCitation("S1"), new ClaimCitation("S99")))));
        RetrievedChunk uncited = new RetrievedChunk(
                "e2", "uncited.pdf", null, 4, "unused", null, null, null, null);
        AnswerQuestionService service = new AnswerQuestionService(
                llm,
                (query, teamId) -> RetrievalOutcome.selectedOnly(List.of(amendmentChunk("used"), uncited)));

        var result = service.execute("question", UUID.randomUUID());

        assertThat(result.citations()).singleElement().satisfies(citation ->
                assertThat(citation.accessionNumber()).isEqualTo("0000320193-25-000020"));
    }

    @Test
    void replacesSourceAliasesWithNumberedMarkersInCitationOrder() {
        RecordingLlm llm = new RecordingLlm();
        llm.answers = new java.util.ArrayDeque<>(List.of(new GroundedAnswer(
                true,
                "Revenue changed.[S2] Risks changed.[S1] Unsupported.[S99]",
                List.of(new ClaimCitation("S2"), new ClaimCitation("S1"), new ClaimCitation("S99")))));
        RetrievedChunk second = new RetrievedChunk(
                "e2", "second.pdf", "MD&A", 4, "revenue evidence",
                "second-accession", "10-Q", LocalDate.of(2025, 5, 1), "Item 2");
        AnswerQuestionService service = new AnswerQuestionService(
                llm,
                (query, teamId) -> RetrievalOutcome.selectedOnly(List.of(amendmentChunk("risk evidence"), second)));

        var result = service.execute("question", UUID.randomUUID());

        assertThat(result.answer()).isEqualTo("Revenue changed.[1] Risks changed.[2] Unsupported.");
        assertThat(result.citations()).extracting(citation -> citation.accessionNumber())
                .containsExactly("second-accession", "0000320193-25-000020");
        assertThat(result.answer()).doesNotContain("S1", "S2", "S99", "e1", "e2");
    }

    @Test
    void finalUnanswerableResultUsesExactRefusalAndNoCitations() {
        RecordingLlm llm = new RecordingLlm();
        GroundedAnswer unsupported = new GroundedAnswer(
                false, "The documents do not say.", List.of(new ClaimCitation("S1")));
        llm.answers = new java.util.ArrayDeque<>(List.of(unsupported, unsupported, unsupported));
        AnswerQuestionService service = new AnswerQuestionService(
                llm,
                (query, teamId) -> RetrievalOutcome.selectedOnly(List.of(amendmentChunk("unrelated"))));

        var result = service.execute("unsupported question", UUID.randomUUID());

        assertThat(result.answer()).isEqualTo(
                "The current document vault does not contain information to answer this question.");
        assertThat(result.citations()).isEmpty();
        assertThat(result.attempts()).extracting(attempt -> attempt.type())
                .containsExactly(AttemptType.ORIGINAL, AttemptType.REWRITE, AttemptType.HYDE);
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
        java.util.ArrayDeque<GroundedAnswer> answers = new java.util.ArrayDeque<>(List.of(groundedAnswer("S1")));

        @Override
        public String rewriteForSearch(String question) {
            return "rewritten";
        }

        @Override
        public String generateHypotheticalAnswer(String question) {
            return question;
        }

        @Override
        public GroundedAnswer answerWithContext(String context, String question) {
            this.context = context;
            return answers.removeFirst();
        }
    }

    private static GroundedAnswer groundedAnswer(String sourceId) {
        return new GroundedAnswer(true, "answer", List.of(new ClaimCitation(sourceId)));
    }
}
