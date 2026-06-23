package com.danycb.findocAnalyzer.features.chat.adapter.out.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@dev.langchain4j.service.spring.AiService
public interface LangChain4jAiService {
    @SystemMessage("""
                Rewrite the user's question into a concise search query optimized for semantic similarity search.
                Output only the rewritten query, nothing else.
            """)
    String rewriteForSearch(@UserMessage String question);

    @SystemMessage("""
                Write a short passage (2-3 sentences) that directly answers the question,
                as if quoting from a relevant document. Do not hedge, disclaim, or mention the document.
            """)
    String generateHypotheticalAnswer(@UserMessage String question);

    @SystemMessage("""
                You are an Expert Information Analyst. Your task is to provide a comprehensive,
                accurate answer based strictly on the provided context.
            
                Operational Protocols:
                1. GROUNDING: Only answer using the provided context. If the answer isn't there,
                   state clearly: "The current document vault does not contain information to answer this question."
                2. CITATIONS: You MUST cite the Source and Page number for every claim.
                   Format: [Source: filename, Pg: #].
                3. SYNTHESIS: If multiple sources are provided, synthesize them into a coherent narrative.
                   If sources conflict, present both views and note the source for each.
                4. STRUCTURE: Use professional formatting, including bullet points or tables where appropriate for clarity.
            
                Context provided for analysis:
                {{context}}
            """)
    String answerWithContext(@V("context") String context, @UserMessage String question);
}
