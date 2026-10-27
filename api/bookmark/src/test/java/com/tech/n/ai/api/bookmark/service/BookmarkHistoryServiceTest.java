package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkHistoryListRequest;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // ========== restoreFromHistory 테스트 ==========

    @Nested
    @DisplayName("restoreFromHistory")
    class RestoreFromHistory {

        private static final Long OWNER_ID = 1L;
        private static final Long BOOKMARK_ID = 100L;
        private static final Long HISTORY_ID = 500L;
        private static final String AFTER_DATA =
            "{\"memo\":\"복원 메모\",\"tag\":\"복원태그\",\"title\":\"복원 제목\"}";

        @Test
        @DisplayName("주인이 자기 북마크의 이력으로 요청하면 afterData 값이 복원되고 저장 결과를 반환")
        void restoreFromHistory_성공() {
            // Given
            BookmarkEntity bookmark = createBookmark(BOOKMARK_ID, OWNER_ID);
            when(bookmarkReaderRepository.findById(BOOKMARK_ID)).thenReturn(Optional.of(bookmark));
            when(bookmarkHistoryReaderRepository.findByHistoryId(HISTORY_ID))
                .thenReturn(Optional.of(createHistory(HISTORY_ID, BOOKMARK_ID, AFTER_DATA)));
            BookmarkEntity saved = new BookmarkEntity();
            when(bookmarkWriterRepository.save(bookmark)).thenReturn(saved);

            // When
            BookmarkEntity result = bookmarkHistoryService.restoreFromHistory(
                OWNER_ID.toString(), BOOKMARK_ID.toString(), HISTORY_ID.toString());

            // Then
            assertThat(result).isSameAs(saved);
            assertThat(bookmark.getMemo()).isEqualTo("복원 메모");
            assertThat(bookmark.getTag()).isEqualTo("복원태그");
            assertThat(bookmark.getTitle()).isEqualTo("복원 제목");
            verify(bookmarkWriterRepository, times(1)).save(bookmark);
        }

        @Test
        @DisplayName("북마크 주인이 아닌 사용자가 요청하면 UnauthorizedException, 북마크는 바뀌지 않음")
        void restoreFromHistory_다른_사용자() {
            // Given
            BookmarkEntity bookmark = createBookmark(BOOKMARK_ID, OWNER_ID);
            when(bookmarkReaderRepository.findById(BOOKMARK_ID)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkHistoryService.restoreFromHistory(
                "2", BOOKMARK_ID.toString(), HISTORY_ID.toString()))
                .isInstanceOf(UnauthorizedException.class);
            assertOriginalFields(bookmark);
            verify(bookmarkHistoryReaderRepository, never()).findByHistoryId(any());
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("이력의 bookmarkId가 요청한 북마크와 다르면 BookmarkNotFoundException, 북마크는 바뀌지 않음")
        void restoreFromHistory_다른_북마크의_이력() {
            // Given
            BookmarkEntity bookmark = createBookmark(BOOKMARK_ID, OWNER_ID);
            when(bookmarkReaderRepository.findById(BOOKMARK_ID)).thenReturn(Optional.of(bookmark));
            when(bookmarkHistoryReaderRepository.findByHistoryId(HISTORY_ID))
                .thenReturn(Optional.of(createHistory(HISTORY_ID, 999L, AFTER_DATA)));

            // When & Then
            assertThatThrownBy(() -> bookmarkHistoryService.restoreFromHistory(
                OWNER_ID.toString(), BOOKMARK_ID.toString(), HISTORY_ID.toString()))
                .isInstanceOf(BookmarkNotFoundException.class);
            assertOriginalFields(bookmark);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("북마크가 없으면 BookmarkNotFoundException")
        void restoreFromHistory_북마크_미존재() {
            // Given
            when(bookmarkReaderRepository.findById(BOOKMARK_ID)).thenReturn(Optional.empty());
            lenient().when(bookmarkHistoryReaderRepository.findByHistoryId(HISTORY_ID))
                .thenReturn(Optional.of(createHistory(HISTORY_ID, BOOKMARK_ID, AFTER_DATA)));

            // When & Then
            assertThatThrownBy(() -> bookmarkHistoryService.restoreFromHistory(
                OWNER_ID.toString(), BOOKMARK_ID.toString(), HISTORY_ID.toString()))
                .isInstanceOf(BookmarkNotFoundException.class);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("이력이 없으면 BookmarkNotFoundException")
        void restoreFromHistory_이력_미존재() {
            // Given
            lenient().when(bookmarkReaderRepository.findById(BOOKMARK_ID))
                .thenReturn(Optional.of(createBookmark(BOOKMARK_ID, OWNER_ID)));
            when(bookmarkHistoryReaderRepository.findByHistoryId(HISTORY_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bookmarkHistoryService.restoreFromHistory(
                OWNER_ID.toString(), BOOKMARK_ID.toString(), HISTORY_ID.toString()))
                .isInstanceOf(BookmarkNotFoundException.class);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("entityId·historyId·userId 형식이 틀리면 BookmarkValidationException")
        void restoreFromHistory_잘못된_ID_형식() {
            // When & Then
            assertThatThrownBy(() -> bookmarkHistoryService.restoreFromHistory(
                OWNER_ID.toString(), "invalid", HISTORY_ID.toString()))
                .isInstanceOf(BookmarkValidationException.class);
            assertThatThrownBy(() -> bookmarkHistoryService.restoreFromHistory(
                OWNER_ID.toString(), BOOKMARK_ID.toString(), "invalid"))
                .isInstanceOf(BookmarkValidationException.class);
            assertThatThrownBy(() -> bookmarkHistoryService.restoreFromHistory(
                "invalid", BOOKMARK_ID.toString(), HISTORY_ID.toString()))
                .isInstanceOf(BookmarkValidationException.class);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        private void assertOriginalFields(BookmarkEntity bookmark) {
            assertThat(bookmark.getMemo()).isEqualTo("원래 메모");
            assertThat(bookmark.getTag()).isEqualTo("원래태그");
            assertThat(bookmark.getTitle()).isEqualTo("원래 제목");
        }
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
        private static final LocalDateTime END_OF_LAST_DAY = LocalDateTime.of(2026, 1, 31, 23, 59, 59, 999_999_000);

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

        @Test
        @DisplayName("endDate를 날짜만 주면 그날 23:59:59.999999까지 포함해 기간으로 조회")
        void findHistory_기간만_endDate_날짜만() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkIdAndChangedAtBetween(
                BOOKMARK_ID, START, END_OF_LAST_DAY, PAGEABLE)).thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> result = findHistory(
                new BookmarkHistoryListRequest(1, 10, null, "2026-01-01", "2026-01-31"));

            // Then
            assertThat(result).isSameAs(page);
            verify(bookmarkHistoryReaderRepository, times(1))
                .findByBookmarkIdAndChangedAtBetween(BOOKMARK_ID, START, END_OF_LAST_DAY, PAGEABLE);
        }

        @Test
        @DisplayName("operationType과 함께 endDate를 날짜만 주면 그날 23:59:59.999999까지 포함해 조회")
        void findHistory_작업종류와_기간_endDate_날짜만() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkIdAndOperationTypeAndChangedAtBetween(
                BOOKMARK_ID, "DELETE", START, END_OF_LAST_DAY, PAGEABLE)).thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> result = findHistory(
                new BookmarkHistoryListRequest(1, 10, "DELETE", "2026-01-01", "2026-01-31"));

            // Then
            assertThat(result).isSameAs(page);
            verify(bookmarkHistoryReaderRepository, times(1))
                .findByBookmarkIdAndOperationTypeAndChangedAtBetween(
                    BOOKMARK_ID, "DELETE", START, END_OF_LAST_DAY, PAGEABLE);
        }

        @Test
        @DisplayName("endDate에 소문자 t로 시각까지 주면 날짜만 준 것으로 보지 않고 그 시각 그대로 조회")
        void findHistory_endDate_소문자_t_시각() {
            // Given
            when(bookmarkHistoryReaderRepository.findByBookmarkIdAndChangedAtBetween(
                BOOKMARK_ID, START, END, PAGEABLE)).thenReturn(page);

            // When
            Page<BookmarkHistoryEntity> result = findHistory(
                new BookmarkHistoryListRequest(1, 10, null, "2026-01-01", "2026-01-31t23:59:59"));

            // Then
            assertThat(result).isSameAs(page);
        }

        @Test
        @DisplayName("endDate 형식이 틀리면 필드명 endDate를 담은 BookmarkValidationException")
        void findHistory_endDate_형식_오류() {
            // When & Then
            assertThatThrownBy(() -> findHistory(
                new BookmarkHistoryListRequest(1, 10, null, "2026-01-01", "2026/01/31")))
                .isInstanceOf(BookmarkValidationException.class)
                .hasMessageContaining("endDate");
            verify(bookmarkHistoryReaderRepository, never())
                .findByBookmarkIdAndChangedAtBetween(any(), any(), any(), any());
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

    private BookmarkHistoryEntity createHistory(Long historyId, Long bookmarkId, String afterData) {
        BookmarkHistoryEntity history = new BookmarkHistoryEntity();
        history.setHistoryId(historyId);
        history.setBookmarkId(bookmarkId);
        history.setOperationType("UPDATE");
        history.setAfterData(afterData);
        return history;
    }
}
