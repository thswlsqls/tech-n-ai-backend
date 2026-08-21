package com.tech.n.ai.api.bookmark.dto.response;

import java.time.LocalDateTime;

/**
 * 조회 이벤트 기록 응답
 *
 * @param bookmarkId 조회한 북마크 ID
 * @param viewedAt 기록 시각
 * @param todayViewCount 기록 후 그 날짜의 누적 조회 수
 */
public record BookmarkViewEventResponse(
    Long bookmarkId,
    LocalDateTime viewedAt,
    Long todayViewCount
) {
    public static BookmarkViewEventResponse of(Long bookmarkId, LocalDateTime viewedAt, Long todayViewCount) {
        return new BookmarkViewEventResponse(bookmarkId, viewedAt, todayViewCount);
    }
}
