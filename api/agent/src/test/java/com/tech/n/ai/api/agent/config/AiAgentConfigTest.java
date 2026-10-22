package com.tech.n.ai.api.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiAgentConfig 단위 테스트
 */
@DisplayName("AiAgentConfig 단위 테스트")
class AiAgentConfigTest {

    private static AiAgentConfig createConfig() {
        AiAgentConfig config = new AiAgentConfig();
        ReflectionTestUtils.setField(config, "openAiApiKey", "test-api-key");
        ReflectionTestUtils.setField(config, "agentModelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "agentTemperature", 0.3);
        ReflectionTestUtils.setField(config, "agentMaxTokens", 4096);
        ReflectionTestUtils.setField(config, "agentTimeout", 120);
        ReflectionTestUtils.setField(config, "logRequests", false);
        ReflectionTestUtils.setField(config, "logResponses", false);
        return config;
    }

    @Nested
    @DisplayName("agentChatLanguageModel")
    class AgentChatLanguageModel {

        @Test
        @DisplayName("Agent 모델은 병렬 Tool 호출을 끈 상태로 만들어진다")
        void agentChatLanguageModel_병렬Tool호출_비활성() {
            // Given
            AiAgentConfig config = createConfig();

            // When
            ChatModel model = config.agentChatLanguageModel();

            // Then
            assertThat(model).isInstanceOf(OpenAiChatModel.class);
            assertThat(((OpenAiChatModel) model).defaultRequestParameters().parallelToolCalls()).isFalse();
        }
    }

    @Nested
    @DisplayName("agentTitleChatModel")
    class AgentTitleChatModel {

        @Test
        @DisplayName("제목 생성 모델은 Tool 없는 요청에 쓰이므로 병렬 Tool 호출 설정을 넣지 않는다")
        void agentTitleChatModel_병렬Tool호출_미설정() {
            // Given
            AiAgentConfig config = createConfig();

            // When
            ChatModel model = config.agentTitleChatModel();

            // Then
            assertThat(model).isInstanceOf(OpenAiChatModel.class);
            assertThat(((OpenAiChatModel) model).defaultRequestParameters().parallelToolCalls()).isNull();
        }
    }
}
