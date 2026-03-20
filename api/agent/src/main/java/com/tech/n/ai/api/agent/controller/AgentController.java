package com.tech.n.ai.api.agent.controller;

import com.tech.n.ai.api.agent.agent.AgentExecutionResult;
import com.tech.n.ai.api.agent.dto.request.AgentMessageListRequest;
import com.tech.n.ai.api.agent.dto.request.AgentRunRequest;
import com.tech.n.ai.api.agent.dto.request.AgentSessionListRequest;
import com.tech.n.ai.api.agent.dto.request.UpdateSessionTitleRequest;
import com.tech.n.ai.api.agent.dto.response.AgentMessageListResponse;
import com.tech.n.ai.api.agent.dto.response.AgentSessionListResponse;
import com.tech.n.ai.api.agent.facade.AgentFacade;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import com.tech.n.ai.common.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Emerging Tech Agent REST API 컨트롤러
 * Gateway에서 JWT 역할 기반(ADMIN) 인증을 수행합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentFacade agentFacade;
    private final ConversationSessionService conversationSessionService;

    /**
     * Agent 수동 실행
     *
     * POST /api/v1/agent/run
     * 인증: Gateway에서 JWT ADMIN 역할 검증
     *
     * @param request goal (필수), sessionId (선택)
     * @param userId  Gateway가 주입한 사용자 ID 헤더
     */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<AgentExecutionResult>> runAgent(
            @Valid @RequestBody AgentRunRequest request,
            @RequestHeader("x-user-id") String userId) {

        AgentExecutionResult result = agentFacade.runAgent(userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 세션 목록 조회
     *
     * GET /api/v1/agent/sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<AgentSessionListResponse>> getSessions(
            @Valid AgentSessionListRequest request,
            @RequestHeader("x-user-id") String userId) {

        Pageable pageable = PageRequest.of(request.page() - 1, request.size(),
            Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        AgentSessionListResponse response = agentFacade.listSessions(userId, request.page(), request.size(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 세션 상세 조회
     *
     * GET /api/v1/agent/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<SessionResponse>> getSession(
            @PathVariable String sessionId,
            @RequestHeader("x-user-id") String userId) {

        SessionResponse response = conversationSessionService.getSession(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 대화 이력 조회
     *
     * GET /api/v1/agent/sessions/{sessionId}/messages
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<AgentMessageListResponse>> getMessages(
            @PathVariable String sessionId,
            @Valid AgentMessageListRequest request,
            @RequestHeader("x-user-id") String userId) {

        Pageable pageable = PageRequest.of(request.page() - 1, request.size(),
            Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        AgentMessageListResponse response = agentFacade.listMessages(sessionId, userId, request.page(), request.size(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 세션 타이틀 수동 변경
     *
     * PATCH /api/v1/agent/sessions/{sessionId}/title
     */
    @PatchMapping("/sessions/{sessionId}/title")
    public ResponseEntity<ApiResponse<SessionResponse>> updateSessionTitle(
            @PathVariable String sessionId,
            @Valid @RequestBody UpdateSessionTitleRequest request,
            @RequestHeader("x-user-id") String userId) {

        SessionResponse response = conversationSessionService.updateSessionTitle(
            sessionId, userId, request.title());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 세션 삭제
     *
     * DELETE /api/v1/agent/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable String sessionId,
            @RequestHeader("x-user-id") String userId) {

        conversationSessionService.deleteSession(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
