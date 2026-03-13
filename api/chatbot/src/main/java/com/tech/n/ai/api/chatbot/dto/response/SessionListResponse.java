package com.tech.n.ai.api.chatbot.dto.response;

import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.core.dto.PageData;

/**
 * 세션 목록 조회 응답 DTO
 */
public record SessionListResponse(
    PageData<SessionResponse> data
) {
    public static SessionListResponse from(PageData<SessionResponse> pageData) {
        return new SessionListResponse(pageData);
    }
}
