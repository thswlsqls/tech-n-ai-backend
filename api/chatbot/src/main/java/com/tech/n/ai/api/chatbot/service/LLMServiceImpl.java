package com.tech.n.ai.api.chatbot.service;

import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * LLM 서비스 구현체
 */
@Slf4j
@Service
public class LLMServiceImpl implements LLMService {

    /** 호출 지연시간. 실패한 호출도 기록해야 이 미터의 건수가 실패율의 분모가 된다 */
    private static final String METER_LLM_DURATION = "chatbot.llm.duration";
    private static final String METER_LLM_ERRORS = "chatbot.llm.errors";

    private final ChatModel chatModel;

    // 첫 호출을 기다리지 않고 실행 직후부터 /actuator/prometheus에 나오도록 여기서 등록한다
    private final Timer llmTimer;
    private final Counter llmErrors;

    public LLMServiceImpl(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.llmTimer = Timer.builder(METER_LLM_DURATION).register(meterRegistry);
        this.llmErrors = Counter.builder(METER_LLM_ERRORS).register(meterRegistry);
    }

    @Override
    public String generate(String prompt) {
        long startNanos = System.nanoTime();
        try {
            return chatModel.chat(prompt);
        } catch (Exception e) {
            llmErrors.increment();
            log.error("Failed to generate LLM response", e);
            throw new RuntimeException("LLM 응답 생성 실패", e);
        } finally {
            llmTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
    }
}
