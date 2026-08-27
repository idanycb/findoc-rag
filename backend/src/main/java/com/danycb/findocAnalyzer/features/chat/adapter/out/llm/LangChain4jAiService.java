package com.danycb.findocAnalyzer.features.chat.adapter.out.llm;

import com.danycb.findocAnalyzer.features.chat.domain.GroundedAnswer;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@dev.langchain4j.service.spring.AiService
public interface LangChain4jAiService {
    @SystemMessage(fromResource = "/prompts/rewrite_for_search.md")
    String rewriteForSearch(@UserMessage String question);

    @SystemMessage(fromResource = "/prompts/hyde.md")
    String generateHypotheticalAnswer(@UserMessage String question);

    @SystemMessage(fromResource = "/prompts/answer_with_context.md")
    GroundedAnswer answerWithContext(@V("context") String context, @UserMessage String question);
}
