package com.tech.n.ai.api.agent.agent;

import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices용 Assistant 인터페이스
 *
 * <p>@MemoryId를 통해 세션별 대화 메모리를 분리하여 멀티 유저 환경을 지원합니다.
 * <p>ChatMemoryAccess를 확장하여 세션 메모리 조회/제거를 지원합니다.
 */
public interface AgentAssistant extends ChatMemoryAccess {

    /**
     * 사용자 메시지에 응답
     *
     * @param sessionId 세션 식별자 (멀티 유저 지원용)
     * @param userMessage 사용자 메시지 (System Prompt 포함)
     * @return Agent 응답
     */
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}
