package com.danycb.findocAnalyzer.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

;

@dev.langchain4j.service.spring.AiService
public interface AiEngine {
    @SystemMessage("""
                You are an elite Financial Auditor and Senior Analyst. 
                Your task is to generate a high-density executive summary of a financial document.
            
                Use the provided Metadata to understand the source and the Content Snippet to 
                identify the specific financial substance.
            
                ### Constraints:
                1. Focus on: Document Identity, Stakeholders, and Financial Risks/Health.
                2. Output MUST be exactly three concise bullet points.
                3. Do not use introductory filler.
            
                ### Context:
                Metadata: {{metadata}}
            """)
    String analyzeDeepContent(@V("metadata") String metadata, @UserMessage String text);

    @SystemMessage("""
                You are a Financial Assistant. Answer the user's question using ONLY the
                provided context. If the answer is not in the context, state that you
                do not have enough information based on the documents provided.
            
                Context:
                {{context}}
            """)
    String answerWithContext(@V("context") String context, @UserMessage String question);
}
