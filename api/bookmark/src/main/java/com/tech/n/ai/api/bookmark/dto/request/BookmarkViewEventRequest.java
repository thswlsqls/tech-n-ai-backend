package com.tech.n.ai.api.bookmark.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 북마크 조회 이벤트 기록 요청
 *
 * @param source 조회 경로 (web, app)
 */
public record BookmarkViewEventRequest(
    @Size(max = 20, message = "source는 20자를 넘을 수 없습니다.")
    String source
) {
}
