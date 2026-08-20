package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkDailyReportRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;

/**
 * 북마크 조회 리포트 서비스
 */
public interface BookmarkReportService {

    /**
     * 요청 구간의 일별 조회 집계를 낸다.
     */
    BookmarkDailyReportResponse getDailyReport(Long userId, BookmarkDailyReportRequest request);
}
