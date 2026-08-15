package com.tech.n.ai.api.chatbot.config;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LangChain4jConfig 단위 테스트
 */
@DisplayName("LangChain4jConfig 단위 테스트")
class LangChain4jConfigTest {

    private LangChain4jConfig config(Double temperature) {
        LangChain4jConfig config = new LangChain4jConfig();
        ReflectionTestUtils.setField(config, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(config, "chatModelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "chatModelTemperature", temperature);
        return config;
    }

    @Nested
    @DisplayName("chatModel - temperature 주입")
    class ChatModelTemperature {

        @Test
        @DisplayName("설정으로 받은 temperature가 모델 기본 파라미터에 들어간다")
        void usesInjectedTemperature() {
            // Given: 평가 실행이 쓰는 값인 0을 주입한다
            ChatModel chatModel = config(0.0).chatModel();

            // When
            Double temperature = chatModel.defaultRequestParameters().temperature();

            // Then
            assertThat(temperature).isEqualTo(0.0);
        }

        @Test
        @DisplayName("기본값 0.7도 그대로 전달된다")
        void usesDefaultTemperature() {
            // Given
            ChatModel chatModel = config(0.7).chatModel();

            // When
            Double temperature = chatModel.defaultRequestParameters().temperature();

            // Then
            assertThat(temperature).isEqualTo(0.7);
        }
    }
}
