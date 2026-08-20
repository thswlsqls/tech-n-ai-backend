package com.tech.n.ai.api.bookmark.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 일별 조회 리포트 요청
 *
 * @param from 시작일 (yyyy-MM-dd)
 * @param to 종료일 (yyyy-MM-dd)
 * @param provider 제공자 필터 (선택)
 */
public record BookmarkDailyReportRequest(
    @NotBlank(message = "from은 필수입니다.")
    String from,

    @NotBlank(message = "to는 필수입니다.")
    String to,

    String provider
) {
}
