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

    /**
     * 빈 문자열로 온 provider 는 미지정과 같게 다룬다.
     * `?provider=` 처럼 값 없이 보내는 경로가 있어서, 여기서 null 로 맞춰 두지 않으면
     * 빈 문자열인 행을 찾다가 결과가 비어 버린다.
     */
    public BookmarkDailyReportRequest {
        if (provider != null && provider.isBlank()) {
            provider = null;
        }
    }
}
