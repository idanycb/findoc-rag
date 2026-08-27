package com.danycb.findocAnalyzer.features.chat.adapter.in.web;

import com.danycb.findocAnalyzer.features.chat.application.dto.AnswerResult;
import com.danycb.findocAnalyzer.features.chat.application.in.AnswerQuestionUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalChatControllerProfileTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EvalChatController.class, Dependencies.class);

    @Test
    void evalEndpointIsAbsentWithoutEvalProfile() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(EvalChatController.class));
    }

    @Test
    void evalEndpointExistsWithEvalProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=eval")
                .run(context -> assertThat(context).hasSingleBean(EvalChatController.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean
        AnswerQuestionUseCase answerQuestionUseCase() {
            return (question, teamId) -> new AnswerResult("", List.of());
        }
    }
}
