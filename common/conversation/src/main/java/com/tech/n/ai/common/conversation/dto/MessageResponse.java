package com.tech.n.ai.common.conversation.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 메시지 응답 DTO
 */
@Builder
public record MessageResponse(
    String messageId,
    String sessionId,
    String role,
    String content,
    Integer tokenCount,
    Integer sequenceNumber,
    LocalDateTime createdAt
) {}
