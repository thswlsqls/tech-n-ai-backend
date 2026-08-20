package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkViewEventRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkViewEventResponse;
import com.tech.n.ai.common.exception.exception.ForbiddenException;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkViewEventEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkDailyStatReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkDailyStatWriterJpaRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkViewEventWriterJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

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

    /**
     * KST 로 고정한 시계. 운영 컨테이너의 JVM 기본 존에 기대지 않는다.
     */
    private final Clock bookmarkClock;

    /**
     * 이벤트 저장과 집계 갱신을 한 트랜잭션으로 묶는다.
     * 이벤트만 남고 집계가 안 오르면 리포트 값과 원본이 어긋나고,
     * 그 차이는 재집계 전까지 드러나지 않는다.
     */
    @Override
    @Transactional
    public BookmarkViewEventResponse recordView(Long userId, Long bookmarkId, BookmarkViewEventRequest request) {
        BookmarkEntity bookmark = findViewableBookmark(userId, bookmarkId);

        ZonedDateTime now = ZonedDateTime.now(bookmarkClock);
        LocalDateTime viewedAt = now.toLocalDateTime();
        LocalDate statDate = now.toLocalDate();

        String source = request == null ? null : request.source();
        bookmarkViewEventWriterJpaRepository.save(
            BookmarkViewEventEntity.of(bookmarkId, userId, bookmark.getProvider(), viewedAt, source));

        Long todayViewCount = increaseDailyStat(userId, bookmark.getProvider(), statDate);

        return BookmarkViewEventResponse.of(bookmarkId, viewedAt, todayViewCount);
    }

    /**
     * 조회 이벤트를 남길 수 있는 북마크인지 본다.
     * 소유자 검사가 먼저다 — 남의 북마크가 삭제됐는지를 알려 주지 않기 위해서다.
     */
    private BookmarkEntity findViewableBookmark(Long userId, Long bookmarkId) {
        BookmarkEntity bookmark = bookmarkReaderRepository.findById(bookmarkId)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다: " + bookmarkId));

        if (!bookmark.isOwnedBy(userId)) {
            throw new ForbiddenException("본인의 북마크만 조회할 수 있습니다.");
        }
        if (Boolean.TRUE.equals(bookmark.getIsDeleted())) {
            throw new BookmarkNotFoundException("삭제된 북마크입니다: " + bookmarkId);
        }
        return bookmark;
    }

    /**
     * 그 날짜의 집계를 1 올리고 갱신 결과를 돌려준다.
     *
     * 갱신 행이 0이면 그 날짜 첫 조회다. 이때 두 요청이 동시에 INSERT 를 시도하면
     * UNIQUE 제약에 걸리므로, 진 쪽은 UPDATE 로 한 번 더 간다.
     */
    private Long increaseDailyStat(Long userId, String provider, LocalDate statDate) {
        int updated = bookmarkDailyStatWriterJpaRepository.increaseViewCount(userId, statDate, provider);

        if (updated == 0) {
            BookmarkDailyStatEntity stat = BookmarkDailyStatEntity.of(userId, statDate, provider);
            stat.increaseViewCount();
            try {
                bookmarkDailyStatWriterJpaRepository.saveAndFlush(stat);
                return stat.getViewCount();
            } catch (DataIntegrityViolationException e) {
                log.info("일별 집계 첫 행 생성이 경합했다. UPDATE 로 재시도한다. userId={} statDate={} provider={}",
                    userId, statDate, provider);
                bookmarkDailyStatWriterJpaRepository.increaseViewCount(userId, statDate, provider);
            }
        }

        return bookmarkDailyStatReaderRepository
            .findByUserIdAndStatDateAndProviderAndIsDeletedFalse(userId, statDate, provider)
            .map(BookmarkDailyStatEntity::getViewCount)
            .orElse(0L);
    }
}
