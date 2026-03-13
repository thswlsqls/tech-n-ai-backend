package com.tech.n.ai.api.agent.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Agent 세션 목록 조회 요청 DTO
 */
public record AgentSessionListRequest(
    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
    Integer page,

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    Integer size
) {
    public AgentSessionListRequest {
        if (page == null) {
            page = 1;
        }
        if (size == null) {
            size = 20;
        }
    }
}
