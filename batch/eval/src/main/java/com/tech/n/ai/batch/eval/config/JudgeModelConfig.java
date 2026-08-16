package com.tech.n.ai.batch.eval.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 답변을 채점하는 판정 모델 설정
 *
 * 답변을 만드는 모델(gpt-4o-mini)과 다른 모델을 써서 자기 답을 후하게 보는 편향을 줄인다.
 * API 키는 생성 모델과 같은 것을 쓴다.
 */
@Slf4j
@Configuration
public class JudgeModelConfig {

    /**
     * 채점은 같은 답변에 같은 점수가 나와야 하므로 설정 키로 빼지 않고 0으로 고정한다.
     */
    public static final double JUDGE_TEMPERATURE = 0.0;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Value("${eval.judge.model-name:gpt-4o}")
    private String judgeModelName;

    @Bean("judgeChatModel")
    public OpenAiChatModel judgeChatModel() {
        log.info("판정 모델 초기화: model={}, temperature={}", judgeModelName, JUDGE_TEMPERATURE);

        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(judgeModelName)
            .temperature(JUDGE_TEMPERATURE)
            .maxTokens(300)
            .timeout(Duration.ofSeconds(60))
            // 요청·응답 로깅을 끈다. 프롬프트에 근거 문서 3000토큰이 통째로 들어가 로그가 감당이 안 된다.
            .logRequests(false)
            .logResponses(false)
            .build();
    }
}
