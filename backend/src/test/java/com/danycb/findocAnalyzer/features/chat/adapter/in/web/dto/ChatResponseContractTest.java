package com.danycb.findocAnalyzer.features.chat.adapter.in.web.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseContractTest {

    @Test
    void chatResponseDoesNotExposeDomainCitationAcrossTheWebBoundary() {
        var citationsComponent = Arrays.stream(ChatResponse.class.getRecordComponents())
                .filter(component -> component.getName().equals("citations"))
                .findFirst()
                .orElseThrow();

        assertThat(citationsComponent.getGenericType().getTypeName())
                .contains("features.chat.adapter.in.web.dto")
                .doesNotContain("features.chat.domain.Citation");
    }
}
