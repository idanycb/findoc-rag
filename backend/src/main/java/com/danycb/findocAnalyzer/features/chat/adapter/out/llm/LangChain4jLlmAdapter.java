package com.danycb.findocAnalyzer.features.chat.adapter.out.llm;

import com.danycb.findocAnalyzer.features.chat.application.AiAnalysisException;
import com.danycb.findocAnalyzer.features.chat.application.out.LlmPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LangChain4jLlmAdapter implements LlmPort {
    private final LangChain4jAiService aiService;

    @Override
    public List<String> generateSearchVariations(String question) {
        try {
            return aiService.generateSearchVariations(question);
        } catch (Exception e) {
            throw new AiAnalysisException("Failed to generate search variations", e);
        }
    }

    @Override
    public String selectRelevantIndices(String question, String chunkList) {
        try {
            return aiService.selectRelevantIndices(question, chunkList);
        } catch (Exception e) {
            throw new AiAnalysisException("Failed to select relevant document chunks", e);
        }
    }

    @Override
    public String answerWithContext(String context, String question) {
        try {
            return aiService.answerWithContext(context, question);
        } catch (Exception e) {
            throw new AiAnalysisException("Failed to answer question with document context", e);
        }
    }
}
