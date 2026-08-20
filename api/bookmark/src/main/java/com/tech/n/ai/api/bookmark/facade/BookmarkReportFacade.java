package com.tech.n.ai.api.bookmark.facade;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkDailyReportRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkViewEventRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkViewEventResponse;
import com.tech.n.ai.api.bookmark.service.BookmarkReportService;
import com.tech.n.ai.api.bookmark.service.BookmarkViewEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 조회 이벤트·리포트 Facade
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkReportFacade {

    private final BookmarkViewEventService bookmarkViewEventService;
    private final BookmarkReportService bookmarkReportService;

    public BookmarkViewEventResponse recordView(Long userId, String id, BookmarkViewEventRequest request) {
        Long bookmarkId = parseBookmarkId(id);
        return bookmarkViewEventService.recordView(userId, bookmarkId, request);
    }

    public BookmarkDailyReportResponse getDailyReport(Long userId, BookmarkDailyReportRequest request) {
        return bookmarkReportService.getDailyReport(userId, request);
    }

    private Long parseBookmarkId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BookmarkValidationException("유효하지 않은 북마크 ID 형식입니다: " + id);
        }
    }
}
