package com.tech.n.ai.api.agent.agent;


import com.tech.n.ai.api.agent.agent.dto.ChartData;
import com.tech.n.ai.api.agent.config.AgentPromptConfig;
import com.tech.n.ai.api.agent.exception.AgentLoopDetectedException;
import com.tech.n.ai.api.agent.metrics.ToolExecutionMetrics;
import com.tech.n.ai.api.agent.tool.EmergingTechAgentTools;
import com.tech.n.ai.api.agent.tool.handler.ToolErrorHandlers;
import com.tech.n.ai.client.slack.domain.slack.contract.SlackContract;
import com.tech.n.ai.common.conversation.memory.MongoDbChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;


/**
 * Emerging Tech 추적 Agent 구현체
 * LangChain4j AiServices를 사용하여 Tool 기반 자율 실행
 *
 * <p>주요 특징:
 * <ul>
 *   <li>3종 Error Handler로 안전한 Tool 실행 보장</li>
 *   <li>@MemoryId 기반 세션 분리로 멀티 유저/멀티 턴 지원</li>
 *   <li>입력값 검증으로 LLM hallucination 방어</li>
 *   <li>ThreadLocal 기반 메트릭으로 동시 실행 격리</li>
 *   <li>ChatMemoryAccess로 세션 메모리 제거 지원 (메모리 누수 방지)</li>
 * </ul>
 */
@Slf4j
@Service
public class EmergingTechAgentImpl implements EmergingTechAgent {

    private final ChatModel chatModel;
    private final EmergingTechAgentTools tools;
    private final AgentPromptConfig promptConfig;
    private final SlackContract slackContract;
    private final MongoDbChatMemoryStore mongoDbChatMemoryStore;

    // 첫 실행을 기다리지 않고 실행 직후부터 /actuator/prometheus에 나오도록 여기서 등록한다
    private final DistributionSummary toolCalls;

    private static final int MAX_MESSAGES = 30;
    private static final int MAX_TOOL_INVOCATIONS = 30;

    /** 실행 한 번이 Tool을 몇 번 불렀는지. 합계와 건수로 실행당 평균을 봐야 루프인지 판단할 수 있다 */
    private static final String METER_TOOL_CALLS = "agent.tool.calls";

    /** 싱글턴 AgentAssistant - 세션별 ChatMemory를 공유하여 멀티 턴 대화 지원 */
    private AgentAssistant assistant;

    public EmergingTechAgentImpl(
            @Qualifier("agentChatModel") ChatModel chatModel,
            EmergingTechAgentTools tools,
            AgentPromptConfig promptConfig,
            SlackContract slackContract,
            MongoDbChatMemoryStore mongoDbChatMemoryStore,
            MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.tools = tools;
        this.promptConfig = promptConfig;
        this.slackContract = slackContract;
        this.mongoDbChatMemoryStore = mongoDbChatMemoryStore;
        this.toolCalls = DistributionSummary.builder(METER_TOOL_CALLS).register(meterRegistry);
    }

    @PostConstruct
    void initAssistant() {
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(mongoDbChatMemoryStore)
                .build();

        this.assistant = AiServices.builder(AgentAssistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .chatMemoryProvider(memoryProvider)
                .toolExecutionErrorHandler(ToolErrorHandlers::handleToolExecutionError)
                .toolArgumentsErrorHandler(ToolErrorHandlers::handleToolArgumentsError)
                .hallucinatedToolNameStrategy(ToolErrorHandlers::handleHallucinatedToolName)
                .maxSequentialToolsInvocations(MAX_TOOL_INVOCATIONS)
                .build();

        log.info("AgentAssistant 초기화 완료 (멀티 턴 세션 지원)");
    }

    @Override
    public AgentExecutionResult execute(String goal) {
        return execute(goal, generateSessionId());
    }

    @Override
    public AgentExecutionResult execute(String goal, String sessionId) {
        long startTime = System.currentTimeMillis();

        // 실행별 메트릭 생성 및 바인딩 (동시 실행 격리)
        ToolExecutionMetrics metrics = new ToolExecutionMetrics();
        tools.bindMetrics(metrics);

        try {
            String response = assistant.chat(sessionId, promptConfig.buildPrompt(goal));

            long elapsed = System.currentTimeMillis() - startTime;
            int toolCallCount = metrics.getToolCallCount();
            int analyticsCallCount = metrics.getAnalyticsCallCount();
            int validationErrors = metrics.getValidationErrorCount();

            log.info("Agent 실행 완료: goal={}, sessionId={}, toolCalls={}, analyticsCalls={}, validationErrors={}, elapsed={}ms",
                    goal, sessionId, toolCallCount, analyticsCallCount, validationErrors, elapsed);

            return AgentExecutionResult.success(response, sessionId, toolCallCount, analyticsCallCount, elapsed, metrics.getChartData());

        } catch (AgentLoopDetectedException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            int toolCallCount = metrics.getToolCallCount();
            log.warn("Agent 루프 감지로 강제 종료: goal={}, sessionId={}, toolCalls={}, collectBlocked={}, elapsed={}ms",
                    goal, sessionId, toolCallCount, metrics.getCollectBlockedCount(), elapsed);

            String summary = buildSummaryFromChartData(metrics.getChartData(), goal);
            return AgentExecutionResult.success(
                    summary, sessionId, toolCallCount, metrics.getAnalyticsCallCount(), elapsed, metrics.getChartData());

        } catch (RuntimeException e) {
            // langchain4j의 "exceeded N sequential tool invocations" 예외도 루프로 간주
            if (e.getMessage() != null && e.getMessage().contains("exceeded") && e.getMessage().contains("sequential tool")) {
                long elapsed = System.currentTimeMillis() - startTime;
                int toolCallCount = metrics.getToolCallCount();
                log.warn("Agent 최대 Tool 호출 횟수 초과로 강제 종료: goal={}, sessionId={}, toolCalls={}, elapsed={}ms",
                        goal, sessionId, toolCallCount, elapsed);

                String summary = buildSummaryFromChartData(metrics.getChartData(), goal);
                return AgentExecutionResult.success(
                        summary, sessionId, toolCallCount, metrics.getAnalyticsCallCount(), elapsed, metrics.getChartData());
            }

            log.error("Agent 실행 실패: goal={}, sessionId={}", goal, sessionId, e);
            notifyError(goal, e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return AgentExecutionResult.failure(
                    "Agent 실행 중 오류 발생: " + errorMsg,
                    sessionId,
                    List.of(errorMsg)
            );
        } catch (Exception e) {
            log.error("Agent 실행 실패: goal={}, sessionId={}", goal, sessionId, e);
            notifyError(goal, e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return AgentExecutionResult.failure(
                    "Agent 실행 중 오류 발생: " + errorMsg,
                    sessionId,
                    List.of(errorMsg)
            );
        } finally {
            toolCalls.record(metrics.getToolCallCount());
            // ThreadLocal 메트릭 해제 (메모리 누수 방지)
            tools.unbindMetrics();
        }
    }

    /**
     * 세션 메모리 제거
     * 대화 종료 시 호출하여 메모리 누수를 방지합니다.
     *
     * @param sessionId 제거할 세션 ID
     * @return 제거 성공 여부
     */
    public boolean evictSession(String sessionId) {
        try {
            boolean evicted = assistant.evictChatMemory(sessionId);
            if (evicted) {
                log.info("세션 메모리 제거: sessionId={}", sessionId);
            }
            return evicted;
        } catch (Exception e) {
            log.warn("세션 메모리 제거 실패: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * chartData로부터 Markdown 요약을 생성한다.
     * AgentLoopDetectedException 발생 시 LLM이 응답을 생성하지 못했을 때 사용
     */
    private String buildSummaryFromChartData(List<ChartData> chartDataList, String goal) {
        if (chartDataList == null || chartDataList.isEmpty()) {
            return "요청하신 작업을 처리했으나 결과 데이터가 없습니다.";
        }

        var sb = new StringBuilder();
        sb.append("## ").append(goal).append("\n\n");

        for (ChartData chart : chartDataList) {
            sb.append("### ").append(chart.title()).append("\n\n");

            if (chart.meta() != null && chart.meta().totalCount() > 0) {
                sb.append("- **총 건수**: ").append(chart.meta().totalCount()).append("건\n");
                if (chart.meta().startDate() != null && !chart.meta().startDate().isBlank()) {
                    sb.append("- **조회 기간**: ").append(chart.meta().startDate())
                      .append(" ~ ").append(chart.meta().endDate()).append("\n");
                } else {
                    sb.append("- **조회 기간**: 전체 기간\n");
                }
                sb.append("\n");
            }

            if (chart.dataPoints() != null && !chart.dataPoints().isEmpty()) {
                sb.append("| 항목 | 건수 | 비율 |\n");
                sb.append("|------|------|------|\n");

                long total = chart.dataPoints().stream().mapToLong(ChartData.DataPoint::value).sum();
                for (ChartData.DataPoint dp : chart.dataPoints()) {
                    double pct = total > 0 ? (dp.value() * 100.0 / total) : 0;
                    sb.append("| ").append(dp.label())
                      .append(" | ").append(dp.value())
                      .append(" | ").append(String.format("%.1f%%", pct))
                      .append(" |\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String generateSessionId() {
        return "agent-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void notifyError(String goal, Exception e) {
        try {
            slackContract.sendErrorNotification("Agent 실행 실패\nGoal: " + goal, e);
        } catch (Exception slackError) {
            log.error("Slack 에러 알림 전송 실패", slackError);
        }
    }
}
