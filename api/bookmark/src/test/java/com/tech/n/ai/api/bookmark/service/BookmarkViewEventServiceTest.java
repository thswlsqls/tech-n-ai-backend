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
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookmarkViewEventService 단위 테스트
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

    @InjectMocks
    private BookmarkViewEventServiceImpl bookmarkViewEventService;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_BOOKMARK_ID = 100L;

    @Nested
    @DisplayName("recordView")
    class RecordView {

        @Test
        @DisplayName("첫 조회 - 집계가 1이 된다")
        void recordView_첫조회() {
            when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID))
                .thenReturn(Optional.of(createBookmark(TEST_BOOKMARK_ID, TEST_USER_ID)));
            when(bookmarkDailyStatReaderRepository
                .findByUserIdAndStatDateAndProvider(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());

            BookmarkViewEventResponse response = bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web"));

            assertThat(response.bookmarkId()).isEqualTo(TEST_BOOKMARK_ID);
            assertThat(response.todayViewCount()).isEqualTo(1L);
            verify(bookmarkViewEventWriterJpaRepository, times(1))
                .save(any(BookmarkViewEventEntity.class));
        }

        @Test
        @DisplayName("이미 집계가 있으면 1 올린다")
        void recordView_기존집계증가() {
            BookmarkDailyStatEntity stat =
                BookmarkDailyStatEntity.of(TEST_USER_ID, LocalDate.now(), "github");
            stat.setViewCount(5L);

            when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID))
                .thenReturn(Optional.of(createBookmark(TEST_BOOKMARK_ID, TEST_USER_ID)));
            when(bookmarkDailyStatReaderRepository
                .findByUserIdAndStatDateAndProvider(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.of(stat));

            BookmarkViewEventResponse response = bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web"));

            assertThat(response.todayViewCount()).isEqualTo(6L);
            verify(bookmarkDailyStatWriterJpaRepository, times(1))
                .save(any(BookmarkDailyStatEntity.class));
        }

        @Test
        @DisplayName("없는 북마크 - BookmarkNotFoundException")
        void recordView_없는북마크() {
            when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest("web")))
                .isInstanceOf(BookmarkNotFoundException.class);
        }

        @Test
        @DisplayName("이벤트 원본을 남긴다")
        void recordView_이벤트저장() {
            when(bookmarkReaderRepository.findById(TEST_BOOKMARK_ID))
                .thenReturn(Optional.of(createBookmark(TEST_BOOKMARK_ID, TEST_USER_ID)));
            when(bookmarkDailyStatReaderRepository
                .findByUserIdAndStatDateAndProvider(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());

            BookmarkViewEventResponse response = bookmarkViewEventService.recordView(
                TEST_USER_ID, TEST_BOOKMARK_ID, new BookmarkViewEventRequest(null));

            assertThat(response).isNotNull();
        }
    }

    private BookmarkEntity createBookmark(Long id, Long userId) {
        BookmarkEntity entity = new BookmarkEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setEmergingTechId(new ObjectId().toHexString());
        entity.setTitle("Test Title");
        entity.setUrl("https://example.com");
        entity.setProvider("github");
        return entity;
    }
}
