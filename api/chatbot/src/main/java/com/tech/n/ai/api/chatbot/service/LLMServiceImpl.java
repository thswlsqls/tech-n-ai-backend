package com.tech.n.ai.api.chatbot.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * LLM 서비스 구현체
 */
@Slf4j
@Service
public class LLMServiceImpl implements LLMService {

    /** 호출 지연시간. 실패한 호출도 기록해야 이 미터의 건수가 실패율의 분모가 된다 */
    private static final String METER_LLM_DURATION = "chatbot.llm.duration";
    private static final String METER_LLM_ERRORS = "chatbot.llm.errors";
    /** 제공자가 응답에 실어 보낸 실측 사용량. 추정치가 아니라 실제 청구되는 양이다 */
    private static final String METER_LLM_INPUT_TOKENS = "chatbot.llm.input.tokens";
    private static final String METER_LLM_OUTPUT_TOKENS = "chatbot.llm.output.tokens";

    private final ChatModel chatModel;

    // 첫 호출을 기다리지 않고 실행 직후부터 /actuator/prometheus에 나오도록 여기서 등록한다
    private final Timer llmTimer;
    private final Counter llmErrors;
    private final DistributionSummary inputTokens;
    private final DistributionSummary outputTokens;

    public LLMServiceImpl(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.llmTimer = Timer.builder(METER_LLM_DURATION).register(meterRegistry);
        this.llmErrors = Counter.builder(METER_LLM_ERRORS).register(meterRegistry);
        this.inputTokens = DistributionSummary.builder(METER_LLM_INPUT_TOKENS).register(meterRegistry);
        this.outputTokens = DistributionSummary.builder(METER_LLM_OUTPUT_TOKENS).register(meterRegistry);
    }

    @Override
    public String generate(String prompt) {
        return callWithMetrics(() -> chatModel.chat(List.of(UserMessage.from(prompt))));
    }

    @Override
    public String generate(List<ChatMessage> messages) {
        return callWithMetrics(() -> chatModel.chat(messages));
    }

    // 두 오버로드가 같은 미터 기록 규칙을 쓰도록 호출을 여기로 모은다
    private String callWithMetrics(Supplier<ChatResponse> call) {
        long startNanos = System.nanoTime();
        try {
            ChatResponse response = call.get();
            recordTokenUsage(response.tokenUsage());
            return response.aiMessage().text();
        } catch (Exception e) {
            llmErrors.increment();
            log.error("Failed to generate LLM response", e);
            throw new RuntimeException("LLM 응답 생성 실패", e);
        } finally {
            llmTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
    }

    // 값이 안 실려 오면 0을 넣지 않고 건너뛴다. 0으로 채우면 "0 토큰을 썼다"와 구분이 안 된다.
    private void recordTokenUsage(TokenUsage usage) {
        if (usage == null) {
            return;
        }
        if (usage.inputTokenCount() != null) {
            inputTokens.record(usage.inputTokenCount());
        }
        if (usage.outputTokenCount() != null) {
            outputTokens.record(usage.outputTokenCount());
        }
    }
}
