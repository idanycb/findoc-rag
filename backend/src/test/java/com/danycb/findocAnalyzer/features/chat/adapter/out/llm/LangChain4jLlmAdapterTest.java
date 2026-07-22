package com.danycb.findocAnalyzer.features.chat.adapter.out.llm;

import com.danycb.findocAnalyzer.features.chat.application.AiAnalysisException;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LangChain4jLlmAdapter}: it delegates to the langchain4j AI service and
 * translates any failure into an {@link AiAnalysisException}, giving rate-limit errors a distinct,
 * user-facing message. A hand-written fake stands in for the generated AI service.
 */
class LangChain4jLlmAdapterTest {

    private final FakeAiService aiService = new FakeAiService();
    private final LangChain4jLlmAdapter adapter = new LangChain4jLlmAdapter(aiService);

    @Test
    void rewriteForSearch_passesThroughResult() {
        aiService.rewrite = q -> "rewritten: " + q;

        assertThat(adapter.rewriteForSearch("revenue")).isEqualTo("rewritten: revenue");
    }

    @Test
    void generateHypotheticalAnswer_passesThroughResult() {
        aiService.hypothetical = q -> "hypo: " + q;

        assertThat(adapter.generateHypotheticalAnswer("risks")).isEqualTo("hypo: risks");
    }

    @Test
    void answerWithContext_passesThroughResult() {
        aiService.answer = (ctx, q) -> ctx + "|" + q;

        assertThat(adapter.answerWithContext("context", "question")).isEqualTo("context|question");
    }

    @Test
    void wrapsGenericFailureAsAiAnalysisException() {
        aiService.rewrite = q -> {
            throw new RuntimeException("boom");
        };

        assertThatThrownBy(() -> adapter.rewriteForSearch("q"))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("rewrite query")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void mapsHttp429ToRateLimitMessage() {
        aiService.answer = (ctx, q) -> {
            throw new RuntimeException("status 429 Too Many Requests");
        };

        assertThatThrownBy(() -> adapter.answerWithContext("c", "q"))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("rate-limited");
    }

    @Test
    void mapsRateLimitKeywordToRateLimitMessage() {
        aiService.hypothetical = q -> {
            throw new RuntimeException("rate_limit exceeded for model");
        };

        assertThatThrownBy(() -> adapter.generateHypotheticalAnswer("q"))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("rate-limited");
    }

    // ---- fake -------------------------------------------------------------------------------

    static class FakeAiService implements LangChain4jAiService {
        Function<String, String> rewrite = q -> "";
        Function<String, String> hypothetical = q -> "";
        java.util.function.BinaryOperator<String> answer = (ctx, q) -> "";

        @Override
        public String rewriteForSearch(String question) {
            return rewrite.apply(question);
        }

        @Override
        public String generateHypotheticalAnswer(String question) {
            return hypothetical.apply(question);
        }

        @Override
        public String answerWithContext(String context, String question) {
            return answer.apply(context, question);
        }
    }
}
