package com.danycb.findocAnalyzer.features.chat.application;

import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import com.danycb.findocAnalyzer.features.chat.application.out.VectorSearchPort;
import com.danycb.findocAnalyzer.features.chat.domain.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerQuestionServiceTest {
    @Test
    void contextIncludesSectionTitleWhenAvailable() {
        RecordingLlm llm = new RecordingLlm();
        AnswerQuestionService service = new AnswerQuestionService(
                llm,
                (query, teamId) -> List.of(new RetrievedChunk("e1", "AAPL 10-K FY2024", "Item 1A Risk Factors", 12, "risk text")));

        service.execute("What are the risks?", UUID.randomUUID());

        assertThat(llm.context).contains("[AAPL 10-K FY2024 - Item 1A Risk Factors, Pg 12] risk text");
    }

    @Test
    void contextOmitsBlankSectionTitleGracefully() {
        RecordingLlm llm = new RecordingLlm();
        VectorSearchPort search = (query, teamId) -> List.of(new RetrievedChunk("e1", "upload.pdf", null, 3, "upload text"));
        AnswerQuestionService service = new AnswerQuestionService(llm, search);

        service.execute("Summarize", UUID.randomUUID());

        assertThat(llm.context).contains("[upload.pdf, Pg 3] upload text");
    }

    static class RecordingLlm implements LlmPort {
        String context;

        @Override
        public String rewriteForSearch(String question) {
            return question;
        }

        @Override
        public String generateHypotheticalAnswer(String question) {
            return question;
        }

        @Override
        public String answerWithContext(String context, String question) {
            this.context = context;
            return "answer";
        }
    }
}
