package com.tech.n.ai.batch.graph.config;

import com.tech.n.ai.batch.graph.extract.GraphTokenUsageRecorder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 엔티티·관계를 뽑는 추출 모델 설정
 *
 * api-chatbot의 ChatModel 빈을 빌려 쓰지 않고 여기서 직접 만든다. 챗봇 답변 모델은
 * temperature가 0.7이라 같은 문서에서 매번 다른 그래프가 나오기 때문이다.
 */
@Slf4j
@Configuration
public class GraphExtractionModelConfig {

    /**
     * 같은 문서를 다시 돌렸을 때 같은 그래프가 나와야 재실행 멱등성을 확인할 수 있어 0으로 고정한다.
     */
    public static final double EXTRACTION_TEMPERATURE = 0.0;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Value("${graph.build.model-name:gpt-4o-mini}")
    private String modelName;

    @Bean("graphExtractionChatModel")
    public OpenAiChatModel graphExtractionChatModel(GraphTokenUsageRecorder tokenUsageRecorder) {
        log.info("추출 모델 초기화: model={}, temperature={}", modelName, EXTRACTION_TEMPERATURE);

        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(EXTRACTION_TEMPERATURE)
            .maxTokens(2000)
            .timeout(Duration.ofSeconds(120))
            // 프롬프트에 문서 원문이 통째로 들어가 로그가 감당이 안 된다
            .logRequests(false)
            .logResponses(false)
            .listeners(List.of(tokenUsageRecorder))
            .build();
    }
}
