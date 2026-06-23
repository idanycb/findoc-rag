package com.danycb.findocAnalyzer.features.chat.adapter.out.llm;

import com.danycb.findocAnalyzer.features.chat.application.AiAnalysisException;
import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LangChain4jLlmAdapter implements LlmPort {
    private final LangChain4jAiService aiService;

    @Override
    public String rewriteForSearch(String question) {
        try {
            return aiService.rewriteForSearch(question);
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                throw new AiAnalysisException(
                        "AI service is temporarily rate-limited. Please try again in a moment.", e);
            }
            throw new AiAnalysisException("Failed to rewrite query for search", e);
        }
    }

    @Override
    public String generateHypotheticalAnswer(String question) {
        try {
            return aiService.generateHypotheticalAnswer(question);
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                throw new AiAnalysisException(
                        "AI service is temporarily rate-limited. Please try again in a moment.", e);
            }
            throw new AiAnalysisException("Failed to generate hypothetical answer", e);
        }
    }

    @Override
    public String answerWithContext(String context, String question) {
        try {
            return aiService.answerWithContext(context, question);
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                throw new AiAnalysisException(
                        "AI service is temporarily rate-limited. Please try again in a moment.", e);
            }
            throw new AiAnalysisException("Failed to generate answer from document context", e);
        }
    }

    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("429") || message.contains("rate_limit"));
    }
}
