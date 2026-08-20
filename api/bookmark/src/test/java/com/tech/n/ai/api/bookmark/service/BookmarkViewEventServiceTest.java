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
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookmarkViewEventService 단위 테스트
 *
 * 시계를 KST 2026-08-20 00:30 으로 고정한다. UTC 로는 2026-08-19 15:30 이라
 * 날짜를 UTC 로 자르면 집계가 하루 전 칸으로 간다 — 그 회귀를 여기서 잡는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkViewEventService 단위 테스트")
class BookmarkViewEventServiceTest {

    @Mock
    private BookmarkReaderRepository bookmarkReaderRepository;

    @Mock
    private BookmarkViewEventWriterJpaRepository bookmarkViewEventWriterJpaRepository;

    @Mock
    private BookmarkDailyStatReaderRepository bookmarkDailyStatReaderRepository;

    @Mock
    private BookmarkDailyStatWriterJpaRepository bookmarkDailyStatWriterJpaRepository;

    @Captor
    private ArgumentCaptor<BookmarkViewEventEntity> eventCaptor;

    private BookmarkViewEventServiceImpl bookmarkViewEventService;

    private static final Long TEST_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long TEST_BOOKMARK_ID = 100L;
    private static final String PROVIDER = "github";

    /** KST 2026-08-20 00:30 = UTC 2026-08-19 15:30 */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-19T15:30:00Z");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate EXPECTED_STAT_DATE = LocalDate.of(2026, 8, 20);

    @BeforeEach
    void setUp() {
        bookmarkViewEventService = new BookmarkViewEventServiceImpl(
            bookmarkReaderRepository,
            bookmarkViewEventWriterJpaRepository,
            bookmarkDailyStatReaderRepository,
            bookmarkDailyStatWriterJpaRepository,
            Clock.fixed(FIXED_INSTANT, KST)
        );
    }

    @Nested
    @DisplayName("recordView")
    class RecordView {

        @Test
        @DisplayName("KST 00:30 조회 - 집계 날짜가 그 날짜다 (UTC 로 자르면 전날이 된다)")
        void recordView_KST_자정경계() {
            givenOwnedBookmark();
            when(bookmarkDailyStatWriterJpaRepository
                .increaseViewCount(TEST_USER_ID, EXPECTED_STAT_DATE, PROVIDER)).thenReturn(1);
            givenStatAfterUpdate(3L);

            BookmarkViewEventResponse response = bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web"));

            verify(bookmarkDailyStatWriterJpaRepository, times(1))
                .increaseViewCount(TEST_USER_ID, EXPECTED_STAT_DATE, PROVIDER);
            assertThat(response.viewedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 0, 30));
            assertThat(response.todayViewCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("이벤트 원본에 source 까지 담긴다")
        void recordView_이벤트필드() {
            givenOwnedBookmark();
            when(bookmarkDailyStatWriterJpaRepository
                .increaseViewCount(TEST_USER_ID, EXPECTED_STAT_DATE, PROVIDER)).thenReturn(1);
            givenStatAfterUpdate(1L);

            bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web"));

            verify(bookmarkViewEventWriterJpaRepository).save(eventCaptor.capture());
            BookmarkViewEventEntity saved = eventCaptor.getValue();
            assertThat(saved.getBookmarkId()).isEqualTo(TEST_BOOKMARK_ID);
            assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(saved.getProvider()).isEqualTo(PROVIDER);
            assertThat(saved.getViewedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 0, 30));
            assertThat(saved.getSource()).isEqualTo("web");
        }

        @Test
        @DisplayName("그 날짜 첫 조회 - UPDATE 가 0행이면 INSERT 한다")
        void recordView_첫조회() {
            givenOwnedBookmark();
            when(bookmarkDailyStatWriterJpaRepository
                .increaseViewCount(TEST_USER_ID, EXPECTED_STAT_DATE, PROVIDER)).thenReturn(0);

            BookmarkViewEventResponse response = bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("app"));

            verify(bookmarkDailyStatWriterJpaRepository, times(1))
                .saveAndFlush(org.mockito.ArgumentMatchers.any(BookmarkDailyStatEntity.class));
            assertThat(response.todayViewCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("이미 집계가 있으면 INSERT 하지 않는다")
        void recordView_기존집계증가() {
            givenOwnedBookmark();
            when(bookmarkDailyStatWriterJpaRepository
                .increaseViewCount(TEST_USER_ID, EXPECTED_STAT_DATE, PROVIDER)).thenReturn(1);
            givenStatAfterUpdate(6L);

            BookmarkViewEventResponse response = bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web"));

            verify(bookmarkDailyStatWriterJpaRepository, never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(BookmarkDailyStatEntity.class));
            assertThat(response.todayViewCount()).isEqualTo(6L);
        }

        @Test
        @DisplayName("남의 북마크 - ForbiddenException (403)")
        void recordView_남의북마크() {
            BookmarkEntity other = createBookmark(TEST_BOOKMARK_ID, OTHER_USER_ID);
            when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID)).thenReturn(Optional.of(other));

            assertThatThrownBy(() -> bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web")))
                .isInstanceOf(ForbiddenException.class);

            verify(bookmarkViewEventWriterJpaRepository, never())
                .save(org.mockito.ArgumentMatchers.any(BookmarkViewEventEntity.class));
        }

        @Test
        @DisplayName("삭제된 북마크 - BookmarkNotFoundException (404)")
        void recordView_삭제된북마크() {
            BookmarkEntity deleted = createBookmark(TEST_BOOKMARK_ID, TEST_USER_ID);
            deleted.setIsDeleted(true);
            when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID)).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web")))
                .isInstanceOf(BookmarkNotFoundException.class);

            verify(bookmarkViewEventWriterJpaRepository, never())
                .save(org.mockito.ArgumentMatchers.any(BookmarkViewEventEntity.class));
        }

        @Test
        @DisplayName("없는 북마크 - BookmarkNotFoundException")
        void recordView_없는북마크() {
            when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web")))
                .isInstanceOf(BookmarkNotFoundException.class);
        }
    }

    private void givenOwnedBookmark() {
        when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID))
            .thenReturn(Optional.of(createBookmark(TEST_BOOKMARK_ID, TEST_USER_ID)));
    }

    private void givenStatAfterUpdate(Long viewCount) {
        BookmarkDailyStatEntity stat =
            BookmarkDailyStatEntity.of(TEST_USER_ID, EXPECTED_STAT_DATE, PROVIDER);
        stat.setViewCount(viewCount);
        when(bookmarkDailyStatReaderRepository.findByUserIdAndStatDateAndProviderAndIsDeletedFalse(
            eq(TEST_USER_ID), eq(EXPECTED_STAT_DATE), eq(PROVIDER))).thenReturn(Optional.of(stat));
    }

    private BookmarkEntity createBookmark(Long id, Long userId) {
        BookmarkEntity entity = new BookmarkEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setEmergingTechId(new ObjectId().toHexString());
        entity.setTitle("Test Title");
        entity.setUrl("https://example.com");
        entity.setProvider(PROVIDER);
        return entity;
    }
}
