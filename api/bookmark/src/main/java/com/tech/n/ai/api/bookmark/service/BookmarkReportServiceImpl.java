package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkDailyReportRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkDailyStatReaderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * BookmarkReportService 구현체
 *
 * bookmark_daily_stats 에 쌓아 둔 집계를 날짜 순으로 읽어 내려간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkReportServiceImpl implements BookmarkReportService {

    private final BookmarkDailyStatReaderRepository bookmarkDailyStatReaderRepository;

    @Override
    public BookmarkDailyReportResponse getDailyReport(Long userId, BookmarkDailyReportRequest request) {
        LocalDate from = LocalDate.parse(request.from());
        LocalDate to = LocalDate.parse(request.to());

        List<BookmarkDailyReportResponse.DailyView> days = new ArrayList<>();
        long totalViews = 0L;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<BookmarkDailyStatEntity> stats =
                bookmarkDailyStatReaderRepository.findByUserIdAndStatDate(userId, date);

            for (BookmarkDailyStatEntity stat : stats) {
                if (!matchesProvider(request.provider(), stat.getProvider())) {
                    continue;
                }
                days.add(new BookmarkDailyReportResponse.DailyView(
                    date.toString(), stat.getProvider(), stat.getViewCount()));
                totalViews += stat.getViewCount();
            }
        }

        return new BookmarkDailyReportResponse(request.from(), request.to(), totalViews, days);
    }

    private boolean matchesProvider(String requested, String actual) {
        if (requested == null || requested.isBlank()) {
            return true;
        }
        return requested.equals(actual);
    }
}
