package com.tech.n.ai.api.agent.dto.response;

import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.core.dto.PageData;

/**
 * Agent 세션 목록 조회 응답 DTO
 */
public record AgentSessionListResponse(
    PageData<SessionResponse> data
) {
    public static AgentSessionListResponse from(PageData<SessionResponse> pageData) {
        return new AgentSessionListResponse(pageData);
    }
}
