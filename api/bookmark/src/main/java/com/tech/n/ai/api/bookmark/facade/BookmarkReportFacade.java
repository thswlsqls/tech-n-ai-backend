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
import java.time.temporal.ChronoUnit;

/**
 * 조회 이벤트·리포트 Facade
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkReportFacade {

    private static final long MAX_REPORT_DAYS = 90;

    private final BookmarkViewEventService bookmarkViewEventService;
    private final BookmarkReportService bookmarkReportService;

    public BookmarkViewEventResponse recordView(Long userId, String id, BookmarkViewEventRequest request) {
        Long bookmarkId = parseBookmarkId(id);
        return bookmarkViewEventService.recordView(userId, bookmarkId, request);
    }

    public BookmarkDailyReportResponse getDailyReport(Long userId, BookmarkDailyReportRequest request) {
        LocalDate from = parseDate(request.from(), "from");
        LocalDate to = parseDate(request.to(), "to");
        validateRange(from, to);
        return bookmarkReportService.getDailyReport(userId, from, to, request.provider());
    }

    /**
     * 앞뒤 관계와 구간 길이를 본다.
     * 구간 일수는 from 과 to 를 모두 포함해 센다. 2026-08-01~2026-08-01 은 1일이다.
     */
    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BookmarkValidationException(
                "from은 to보다 늦을 수 없습니다: from=" + from + ", to=" + to);
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_REPORT_DAYS) {
            throw new BookmarkValidationException(
                "조회 구간은 최대 " + MAX_REPORT_DAYS + "일입니다: " + days + "일");
        }
    }

    /**
     * 파싱을 서비스에 맡기면 형식 오류가 DateTimeParseException 으로 새어 나가 500 이 된다.
     */

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
