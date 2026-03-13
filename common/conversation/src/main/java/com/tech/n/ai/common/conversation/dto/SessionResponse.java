package com.tech.n.ai.common.conversation.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 세션 응답 DTO
 */
@Builder
public record SessionResponse(
    String sessionId,
    String title,
    LocalDateTime createdAt,
    LocalDateTime lastMessageAt,
    Boolean isActive
) {}
