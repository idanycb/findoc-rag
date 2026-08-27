package com.danycb.findocAnalyzer.features.chat.adapter.out.llm;

import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PromptResourcesTest {

    @Test
    void systemMessageResourcesExistAndAreNotEmpty() throws IOException {
        Method[] methods = LangChain4jAiService.class.getDeclaredMethods();

        assertThat(methods).hasSize(3);
        for (Method method : methods) {
            SystemMessage systemMessage = method.getAnnotation(SystemMessage.class);
            assertThat(systemMessage).as(method.getName()).isNotNull();

            try (InputStream resource = LangChain4jAiService.class.getResourceAsStream(systemMessage.fromResource())) {
                assertThat(resource).as(systemMessage.fromResource()).isNotNull();
                assertThat(new String(resource.readAllBytes(), StandardCharsets.UTF_8)).isNotBlank();
            }
        }
    }
}
