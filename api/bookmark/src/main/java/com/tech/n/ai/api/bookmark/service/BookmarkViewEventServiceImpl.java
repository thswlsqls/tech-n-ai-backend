package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkViewEventRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkViewEventResponse;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkViewEventEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkDailyStatReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkDailyStatWriterJpaRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkViewEventWriterJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * BookmarkViewEventService 구현체
 *
 * 이벤트 원본은 bookmark_view_events 에 그대로 쌓고,
 * 리포트가 읽을 값은 bookmark_daily_stats 에 갱신해 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkViewEventServiceImpl implements BookmarkViewEventService {

    private final BookmarkReaderRepository bookmarkReaderRepository;
    private final BookmarkViewEventWriterJpaRepository bookmarkViewEventWriterJpaRepository;
    private final BookmarkDailyStatReaderRepository bookmarkDailyStatReaderRepository;
    private final BookmarkDailyStatWriterJpaRepository bookmarkDailyStatWriterJpaRepository;

    @Override
    public BookmarkViewEventResponse recordView(Long userId, Long bookmarkId, BookmarkViewEventRequest request) {
        BookmarkEntity bookmark = bookmarkReaderRepository.findById(bookmarkId)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다: " + bookmarkId));

        LocalDateTime viewedAt = LocalDateTime.now();

        BookmarkViewEventEntity event = BookmarkViewEventEntity.of(
            bookmarkId, userId, bookmark.getProvider(), viewedAt);
        bookmarkViewEventWriterJpaRepository.save(event);

        Long todayViewCount = 0L;
        try {
            todayViewCount = increaseDailyStat(userId, bookmark.getProvider());
        } catch (Exception e) {
            // 집계 갱신이 실패해도 이벤트 원본은 남았으므로 조회 응답은 그대로 돌려준다.
        }

        return BookmarkViewEventResponse.of(bookmarkId, viewedAt, todayViewCount);
    }

    /**
     * 그 날짜의 집계를 1 올린다.
     */
    private Long increaseDailyStat(Long userId, String provider) {
        LocalDate statDate = LocalDate.now(ZoneOffset.UTC);

        BookmarkDailyStatEntity stat = bookmarkDailyStatReaderRepository
            .findByUserIdAndStatDateAndProvider(userId, statDate, provider)
            .orElseGet(() -> BookmarkDailyStatEntity.of(userId, statDate, provider));

        stat.increaseViewCount();
        bookmarkDailyStatWriterJpaRepository.save(stat);

        return stat.getViewCount();
    }
}
