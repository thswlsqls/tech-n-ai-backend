package com.tech.n.ai.api.agent.facade;

import com.tech.n.ai.api.agent.agent.AgentExecutionResult;
import com.tech.n.ai.api.agent.agent.EmergingTechAgent;
import com.tech.n.ai.api.agent.dto.request.AgentRunRequest;
import com.tech.n.ai.api.agent.dto.response.AgentMessageListResponse;
import com.tech.n.ai.api.agent.dto.response.AgentSessionListResponse;
import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationMessageService;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import com.tech.n.ai.common.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent Facade
 * Controller와 Agent 사이의 오케스트레이션 계층
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFacade {

    private final EmergingTechAgent agent;
    private final ConversationSessionService conversationSessionService;
    private final ConversationMessageService conversationMessageService;

    /**
     * Agent 실행
     *
     * @param userId 사용자 ID (로깅 및 세션 관리용)
     * @param request 실행 요청
     * @return 실행 결과
     */
    public AgentExecutionResult runAgent(String userId, AgentRunRequest request) {
        String sessionId = resolveSessionId(userId, request.sessionId());

        log.info("Agent 실행 요청: userId={}, goal={}, sessionId={}",
                userId, request.goal(), sessionId);

        // 사용자 메시지 저장
        conversationMessageService.saveMessage(sessionId, "USER", request.goal(), null);

        // Agent 실행
        AgentExecutionResult result = agent.execute(request.goal(), sessionId);

        // 성공 시에만 ASSISTANT 메시지 저장 (실패 시 에러 텍스트가 대화 이력에 혼입되지 않도록)
        if (result.success()) {
            conversationMessageService.saveMessage(sessionId, "ASSISTANT", result.summary(), null);
        }

        // 세션 마지막 메시지 시간 업데이트
        conversationSessionService.updateLastMessageAt(sessionId);

        return result;
    }

    /**
     * 세션 목록 조회
     */
    public AgentSessionListResponse listSessions(String userId, int page, int size, Pageable pageable) {
        Page<SessionResponse> sessionPage = conversationSessionService.listSessions(userId, pageable);

        List<SessionResponse> list = sessionPage.getContent();

        PageData<SessionResponse> pageData = PageData.of(
            size,
            page,
            (int) sessionPage.getTotalElements(),
            list
        );

        return AgentSessionListResponse.from(pageData);
    }

    /**
     * 메시지 목록 조회
     */
    public AgentMessageListResponse listMessages(String sessionId, String userId, int page, int size, Pageable pageable) {
        conversationSessionService.getSession(sessionId, userId);

        Page<MessageResponse> messagePage = conversationMessageService.getMessages(sessionId, pageable);

        List<MessageResponse> list = messagePage.getContent();

        PageData<MessageResponse> pageData = PageData.of(
            size,
            page,
            (int) messagePage.getTotalElements(),
            list
        );

        return AgentMessageListResponse.from(pageData);
    }

    /**
     * 세션 ID 결정: 요청에 포함되어 있으면 사용, 없으면 새 세션 생성
     */
    private String resolveSessionId(String userId, String requestSessionId) {
        if (requestSessionId != null && !requestSessionId.isBlank()) {
            // 기존 세션 접근 검증
            conversationSessionService.getSession(requestSessionId, userId);
            return requestSessionId;
        }
        return conversationSessionService.createSession(userId, null);
    }
}
