package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkHistoryListRequest;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkHistoryReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkWriterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BookmarkHistoryService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkHistoryService 단위 테스트")
class BookmarkHistoryServiceTest {

    @Mock
    private BookmarkHistoryReaderRepository bookmarkHistoryReaderRepository;

    @Mock
    private BookmarkReaderRepository bookmarkReaderRepository;

    @Mock
    private BookmarkWriterRepository bookmarkWriterRepository;

    private BookmarkHistoryServiceImpl bookmarkHistoryService;

    @BeforeEach
    void setUp() {
        // afterData JSON을 실제로 파싱해야 하므로 ObjectMapper는 실제 객체를 쓴다
        bookmarkHistoryService = new BookmarkHistoryServiceImpl(
            bookmarkHistoryReaderRepository, bookmarkReaderRepository, bookmarkWriterRepository, new ObjectMapper());
    }

    // ========== findHistory 테스트 ==========

    @Nested
    @DisplayName("findHistory")
    class FindHistory {

        private static final Long OWNER_ID = 1L;
        private static final Long BOOKMARK_ID = 100L;
        private static final Pageable PAGEABLE =
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "changedAt"));
        private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
        private static final LocalDateTime END = LocalDateTime.of(2026, 1, 31, 23, 59, 59);

        private final Page<BookmarkHistoryEntity> page = new PageImpl<>(List.of(new BookmarkHistoryEntity()));

        @BeforeEach
        void setUpOwner() {
            when(bookmarkReaderRepository.findById(BOOKMARK_ID))
                .thenReturn(Optional.of(createBookmark(BOOKMARK_ID, OWNER_ID)));
        }

        @Test
        @DisplayName("operationType과 startDate·endDate를 모두 주면 작업 종류와 기간을 함께 걸어 조회")
        void findHistory_작업종류와_기간_함께() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkIdAndOperationTypeAndChangedAtBetween(
                BOOKMARK_ID, "DELETE", START, END, PAGEABLE)).thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> result = findHistory(
                new BookmarkHistoryListRequest(1, 10, "DELETE", "2026-01-01", "2026-01-31T23:59:59"));

            // Then
            assertThat(result).isSameAs(page);
            verify(bookmarkHistoryReaderRepository, times(1))
                .findByBookmarkIdAndOperationTypeAndChangedAtBetween(BOOKMARK_ID, "DELETE", START, END, PAGEABLE);
            verify(bookmarkHistoryReaderRepository, never())
                .findByBookmarkIdAndChangedAtBetween(any(), any(), any(), any());
        }

        @Test
        @DisplayName("operationType만 주면 작업 종류로만 조회")
        void findHistory_작업종류만() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkIdAndOperationType(BOOKMARK_ID, "UPDATE", PAGEABLE))
                .thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> result = findHistory(
                new BookmarkHistoryListRequest(1, 10, "UPDATE", null, null));

            // Then
            assertThat(result).isSameAs(page);
        }

        @Test
        @DisplayName("startDate·endDate만 주면 기간으로만 조회")
        void findHistory_기간만() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkIdAndChangedAtBetween(
                BOOKMARK_ID, START, END, PAGEABLE)).thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> result = findHistory(
                new BookmarkHistoryListRequest(1, 10, null, "2026-01-01", "2026-01-31T23:59:59"));

            // Then
            assertThat(result).isSameAs(page);
        }

        @Test
        @DisplayName("필터를 하나도 주지 않으면 북마크의 전체 이력을 조회")
        void findHistory_필터_없음() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkId(BOOKMARK_ID, PAGEABLE)).thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> result = findHistory(
                new BookmarkHistoryListRequest(1, 10, null, null, null));

            // Then
            assertThat(result).isSameAs(page);
        }

        @Test
        @DisplayName("operationType이 있고 startDate·endDate 중 하나만 있으면 작업 종류로만 조회")
        void findHistory_작업종류와_날짜_한쪽만() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkIdAndOperationType(BOOKMARK_ID, "DELETE", PAGEABLE))
                .thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> onlyStart = findHistory(
                new BookmarkHistoryListRequest(1, 10, "DELETE", "2026-01-01", null));
            Page<BookmarkHistoryEntity> onlyEnd = findHistory(
                new BookmarkHistoryListRequest(1, 10, "DELETE", null, "2026-01-31"));

            // Then
            assertThat(onlyStart).isSameAs(page);
            assertThat(onlyEnd).isSameAs(page);
            verify(bookmarkHistoryReaderRepository, times(2))
                .findByBookmarkIdAndOperationType(BOOKMARK_ID, "DELETE", PAGEABLE);
        }

        private Page<BookmarkHistoryEntity> findHistory(BookmarkHistoryListRequest request) {
            return bookmarkHistoryService.findHistory(OWNER_ID.toString(), BOOKMARK_ID.toString(), request);
        }
    }

    // ========== 헬퍼 메서드 ==========

    private BookmarkEntity createBookmark(Long id, Long userId) {
        BookmarkEntity bookmark = new BookmarkEntity();
        bookmark.setId(id);
        bookmark.setUserId(userId);
        bookmark.setMemo("원래 메모");
        bookmark.setTag("원래태그");
        bookmark.setTitle("원래 제목");
        return bookmark;
    }
}
