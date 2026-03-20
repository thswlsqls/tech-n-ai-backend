package com.tech.n.ai.api.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 세션 타이틀 수동 변경 요청 DTO
 */
public record UpdateSessionTitleRequest(
    @NotBlank(message = "타이틀은 필수입니다.")
    @Size(max = 200, message = "타이틀은 200자를 초과할 수 없습니다.")
    String title
) {}
