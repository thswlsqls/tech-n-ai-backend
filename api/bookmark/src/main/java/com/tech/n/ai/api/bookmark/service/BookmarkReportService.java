package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;

import java.time.LocalDate;

/**
 * 북마크 조회 리포트 서비스
 */
public interface BookmarkReportService {

    /**
     * 요청 구간의 일별 조회 집계를 낸다.
     * 날짜 파싱과 구간 검증은 Facade 가 끝낸 뒤 부른다.
     */
    BookmarkDailyReportResponse getDailyReport(Long userId, LocalDate from, LocalDate to, String provider);
}
