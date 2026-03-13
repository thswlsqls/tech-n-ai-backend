package com.tech.n.ai.api.agent.dto.response;

import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.core.dto.PageData;

/**
 * Agent 메시지 목록 조회 응답 DTO
 */
public record AgentMessageListResponse(
    PageData<MessageResponse> data
) {
    public static AgentMessageListResponse from(PageData<MessageResponse> pageData) {
        return new AgentMessageListResponse(pageData);
    }
}
