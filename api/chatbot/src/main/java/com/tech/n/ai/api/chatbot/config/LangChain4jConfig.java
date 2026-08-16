package com.tech.n.ai.api.chatbot.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * langchain4j 통합 설정
 * 
 * OpenAI GPT-4o-mini (LLM)와 text-embedding-3-small (Embedding Model)을 사용합니다.
 */
@Slf4j
@Configuration
public class LangChain4jConfig {
    
    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;
    
    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}")
    private String chatModelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private Double chatModelTemperature;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String embeddingApiKey;
    
    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-3-small}")
    private String embeddingModelName;
    
    @Value("${langchain4j.open-ai.embedding-model.dimensions:1536}")
    private Integer dimensions;
    
    /**
     * LLM Chat Model Bean (OpenAI GPT-4o-mini - 기본, 비용 최적화)
     */
    @Bean
    @Primary
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(chatModelName)
            .temperature(chatModelTemperature)
            .maxTokens(2000)
            .timeout(Duration.ofSeconds(60))
            .logRequests(true)
            .logResponses(true)
            .build();
    }
    
    /**
     * Embedding Model Bean (OpenAI text-embedding-3-small - LLM Provider와 동일, 통합성 최적화)
     * 비용: $0.02 per 1M tokens, 기본 차원: 1536
     * 참고: OpenAI Embedding Model은 document/query 구분 없이 동일한 모델 사용
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
            .apiKey(embeddingApiKey)  // LLM과 동일한 API Key 사용 가능
            .modelName(embeddingModelName)
            .dimensions(dimensions)  // 기본값: 1536 (필요시 dimensions 파라미터로 조정 가능)
            .timeout(Duration.ofSeconds(30))
            .logRequests(true)
            .logResponses(true)
            .build();
    }
    
    /**
     * OpenAiTokenCountEstimator Bean (TokenService용)
     * OpenAI 모델의 토큰 수를 정확하게 추정합니다.
     */
    @Bean
    public OpenAiTokenCountEstimator openAiTokenCountEstimator() {
        return new OpenAiTokenCountEstimator(chatModelName);
    }
}
