package com.danycb.findocAnalyzer.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

@dev.langchain4j.service.spring.AiService
public interface AiEngine {
    @SystemMessage("""
            You are an expert Information Retrieval Optimizer. Your goal is to expand a user's
            query into 3-4 diverse search variations to ensure maximum recall from a vector database.
            
            Given a question, generate variations that:
            1. Break down complex multi-part questions into individual sub-queries.
            2. Use alternative terminology, technical synonyms, and broader/narrower contexts.
            3. Rephrase as "statements of fact" that might appear in a document (Hypothetical Document Embeddings style).
            
            Return ONLY the queries, one per line. No numbering, no introduction, no bullet points.
            """)
    List<String> generateSearchVariations(@UserMessage String question);

    @SystemMessage("""
                You are a high-precision Retrieval Grader.
                Given a list of document chunks, identify which ones are relevant to answering the question.
            
                Criteria:
                - Must contain direct evidence or critical data.
                - Ignore boilerplate, legal footers, or navigation text.
            
                Return ONLY the indices (e.g., 0, 2, 5) of the relevant chunks, comma-separated.
                If none are relevant, return "NONE".
            
                Question: {{question}}
            """)
    String selectRelevantIndices(@V("question") String question, @UserMessage String chunks);

    @SystemMessage("""
                Analyze the following document content and provide a structured Executive Summary.
            
                Focus on:
                1. MAIN PURPOSE: What is this document's primary objective?
                2. KEY INSIGHTS: What are the 3-5 most critical findings, metrics, or arguments?
                3. TARGET ACTION: Are there any implied or explicit next steps or deadlines?
            
                Metadata: {{metadata}}
            
                Use clean Markdown with bold headers.
            """)
    String analyzeDeepContent(@V("metadata") String metadata, @UserMessage String text);

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
