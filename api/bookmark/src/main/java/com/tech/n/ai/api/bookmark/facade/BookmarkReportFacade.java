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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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
        validateRange(request);
        return bookmarkReportService.getDailyReport(userId, request);
    }

    /**
     * 날짜 형식과 앞뒤 관계를 본다.
     *
     * 파싱을 서비스에 맡기면 형식 오류가 DateTimeParseException 으로 새어 나가 500 이 된다.
     */
    private void validateRange(BookmarkDailyReportRequest request) {
        LocalDate from = parseDate(request.from(), "from");
        LocalDate to = parseDate(request.to(), "to");

        if (from.isAfter(to)) {
            throw new BookmarkValidationException(
                "from은 to보다 늦을 수 없습니다: from=" + request.from() + ", to=" + request.to());
        }
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BookmarkValidationException(
                field + "은(는) yyyy-MM-dd 형식이어야 합니다: " + value);
        }
    }

    private Long parseBookmarkId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BookmarkValidationException("유효하지 않은 북마크 ID 형식입니다: " + id);
        }
    }
}
