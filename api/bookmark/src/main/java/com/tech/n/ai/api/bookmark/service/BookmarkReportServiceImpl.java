package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkDailyStatReaderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * BookmarkReportService 구현체
 *
 * bookmark_daily_stats 에 쌓아 둔 집계를 구간 단위로 한 번에 읽는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkReportServiceImpl implements BookmarkReportService {

    private final BookmarkDailyStatReaderRepository bookmarkDailyStatReaderRepository;

    @Override
    public BookmarkDailyReportResponse getDailyReport(Long userId, LocalDate from, LocalDate to, String provider) {
        List<BookmarkDailyStatEntity> stats =
            bookmarkDailyStatReaderRepository.findRange(userId, from, to, provider);

        List<BookmarkDailyReportResponse.DailyView> days = stats.stream()
            .map(stat -> new BookmarkDailyReportResponse.DailyView(
                stat.getStatDate().toString(), stat.getProvider(), stat.getViewCount()))
            .toList();

        long totalViews = stats.stream()
            .mapToLong(BookmarkDailyStatEntity::getViewCount)
            .sum();

        return new BookmarkDailyReportResponse(from.toString(), to.toString(), totalViews, days);
    }
}
