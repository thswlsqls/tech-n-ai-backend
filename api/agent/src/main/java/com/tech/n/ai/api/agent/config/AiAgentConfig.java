package com.tech.n.ai.api.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI Agent 전용 설정
 * Tool 호출을 지원하는 Agent용 ChatLanguageModel Bean 정의
 */
@Slf4j
@Configuration
public class AiAgentConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}")
    private String agentModelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.3}")
    private Double agentTemperature;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:4096}")
    private Integer agentMaxTokens;

    @Value("${langchain4j.open-ai.chat-model.timeout:120}")
    private Integer agentTimeout;

    @Value("${langchain4j.open-ai.chat-model.log-requests:false}")
    private Boolean logRequests;

    @Value("${langchain4j.open-ai.chat-model.log-responses:false}")
    private Boolean logResponses;

    /**
     * Agent용 ChatLanguageModel (OpenAI GPT-4o-mini)
     * Tool 호출을 지원하는 모델 (낮은 temperature로 일관된 Tool 호출 유도)
     */
    @Bean("agentChatModel")
    public ChatModel agentChatLanguageModel() {
        log.info("Agent ChatLanguageModel 초기화: model={}, temperature={}, logRequests={}, logResponses={}",
                agentModelName, agentTemperature, logRequests, logResponses);

        return baseBuilder()
            // 한 응답에서 Tool 여러 개를 한꺼번에 부르면 결과를 보기 전에
            // 사용자가 요청하지 않은 통계 기준까지 호출하는 일이 있어 끈다
            .parallelToolCalls(false)
            .build();
    }

    /**
     * 세션 제목 생성용 ChatModel
     * 제목 요청에는 Tool이 없어서 parallelToolCalls를 넣으면 OpenAI가 400을 낸다. 그래서 Agent 모델과 따로 둔다.
     */
    @Bean("agentTitleChatModel")
    public ChatModel agentTitleChatModel() {
        return baseBuilder().build();
    }

    private OpenAiChatModel.OpenAiChatModelBuilder baseBuilder() {
        return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(agentModelName)
            .temperature(agentTemperature)
            .maxTokens(agentMaxTokens)
            .timeout(Duration.ofSeconds(agentTimeout))
            .logRequests(logRequests)
            .logResponses(logResponses);
    }
}
