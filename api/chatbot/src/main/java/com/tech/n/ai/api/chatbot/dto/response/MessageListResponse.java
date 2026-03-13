package com.tech.n.ai.api.chatbot.dto.response;

import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.core.dto.PageData;

/**
 * 메시지 목록 조회 응답 DTO
 */
public record MessageListResponse(
    PageData<MessageResponse> data
) {
    public static MessageListResponse from(PageData<MessageResponse> pageData) {
        return new MessageListResponse(pageData);
    }
}
