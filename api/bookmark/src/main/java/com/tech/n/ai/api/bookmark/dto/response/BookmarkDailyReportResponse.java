package com.tech.n.ai.api.bookmark.dto.response;

import java.util.List;

/**
 * 일별 조회 리포트 응답
 *
 * @param from 시작일
 * @param to 종료일
 * @param totalViews 구간 전체 조회 수
 * @param days 날짜별 집계
 */
public record BookmarkDailyReportResponse(
    String from,
    String to,
    Long totalViews,
    List<DailyView> days
) {
    /**
     * 날짜 한 칸
     *
     * @param date 날짜 (yyyy-MM-dd)
     * @param provider 제공자
     * @param views 조회 수
     */
    public record DailyView(
        String date,
        String provider,
        Long views
    ) {
    }
}
