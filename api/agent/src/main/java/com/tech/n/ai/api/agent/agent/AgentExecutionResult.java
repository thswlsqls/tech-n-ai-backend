package com.tech.n.ai.api.agent.agent;

import java.util.List;

/**
 * Agent 실행 결과 DTO
 */
public record AgentExecutionResult(
    boolean success,
    String summary,
    String sessionId,
    int toolCallCount,
    int analyticsCallCount,
    long executionTimeMs,
    List<String> errors
) {
    public static AgentExecutionResult success(String summary, String sessionId, int toolCallCount, int analyticsCallCount, long executionTimeMs) {
        return new AgentExecutionResult(true, summary, sessionId, toolCallCount, analyticsCallCount, executionTimeMs, List.of());
    }

    public static AgentExecutionResult failure(String summary, String sessionId, List<String> errors) {
        return new AgentExecutionResult(false, summary, sessionId, 0, 0, 0, errors);
    }
}
