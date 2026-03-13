package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkListRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkSearchRequest;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BookmarkQueryService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkQueryService 단위 테스트")
class BookmarkQueryServiceTest {

    @Mock
    private BookmarkReaderRepository bookmarkReaderRepository;

    @InjectMocks
    private BookmarkQueryServiceImpl bookmarkQueryService;

    // ========== findBookmarks 테스트 ==========

    @Nested
    @DisplayName("findBookmarks")
    class FindBookmarks {

        @Test
        @DisplayName("기본 조회 - Page 반환")
        void findBookmarks_기본조회() {
            // Given
            Long userId = 1L;
            BookmarkListRequest request = new BookmarkListRequest(1, 10, "createdAt,desc", null);
            List<BookmarkEntity> bookmarks = List.of(
                createBookmark(1L, userId),
                createBookmark(2L, userId)
            );
            Page<BookmarkEntity> page = new PageImpl<>(bookmarks);

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.findBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("provider 필터 적용 조회")
        void findBookmarks_provider필터() {
            // Given
            Long userId = 1L;
            BookmarkListRequest request = new BookmarkListRequest(1, 10, "createdAt,desc", "github");
            BookmarkEntity bookmark = createBookmark(1L, userId);
            bookmark.setProvider("github");
            Page<BookmarkEntity> page = new PageImpl<>(List.of(bookmark));

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.findBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getProvider()).isEqualTo("github");
        }

        @Test
        @DisplayName("빈 결과 반환")
        void findBookmarks_빈결과() {
            // Given
            Long userId = 1L;
            BookmarkListRequest request = new BookmarkListRequest(1, 10, null, null);
            Page<BookmarkEntity> page = new PageImpl<>(List.of());

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.findBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ========== findBookmarkById 테스트 ==========

    @Nested
    @DisplayName("findBookmarkById")
    class FindBookmarkById {

        @Test
        @DisplayName("정상 조회 - BookmarkEntity 반환")
        void findBookmarkById_성공() {
            // Given
            Long userId = 1L;
            Long bookmarkId = 100L;
            BookmarkEntity bookmark = createBookmark(bookmarkId, userId);

            when(bookmarkReaderRepository.findById(bookmarkId)).thenReturn(Optional.of(bookmark));

            // When
            BookmarkEntity result = bookmarkQueryService.findBookmarkById(userId, bookmarkId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(bookmarkId);
        }

        @Test
        @DisplayName("북마크 미존재 시 BookmarkNotFoundException")
        void findBookmarkById_미존재() {
            // Given
            Long userId = 1L;
            Long bookmarkId = 999L;

            when(bookmarkReaderRepository.findById(bookmarkId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bookmarkQueryService.findBookmarkById(userId, bookmarkId))
                .isInstanceOf(BookmarkNotFoundException.class);
        }

        @Test
        @DisplayName("다른 사용자 북마크 조회 시 UnauthorizedException")
        void findBookmarkById_권한없음() {
            // Given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long bookmarkId = 100L;
            BookmarkEntity bookmark = createBookmark(bookmarkId, otherUserId);

            when(bookmarkReaderRepository.findById(bookmarkId)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkQueryService.findBookmarkById(userId, bookmarkId))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("삭제된 북마크 조회 시 BookmarkNotFoundException")
        void findBookmarkById_삭제됨() {
            // Given
            Long userId = 1L;
            Long bookmarkId = 100L;
            BookmarkEntity bookmark = createBookmark(bookmarkId, userId);
            bookmark.setIsDeleted(true);

            when(bookmarkReaderRepository.findById(bookmarkId)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkQueryService.findBookmarkById(userId, bookmarkId))
                .isInstanceOf(BookmarkNotFoundException.class);
        }
    }

    // ========== searchBookmarks 테스트 ==========

    @Nested
    @DisplayName("searchBookmarks")
    class SearchBookmarks {

        @Test
        @DisplayName("전체 검색 (tag + memo)")
        void searchBookmarks_전체검색() {
            // Given
            Long userId = 1L;
            BookmarkSearchRequest request = new BookmarkSearchRequest("AI", 1, 10, "all");
            BookmarkEntity bookmark = createBookmark(1L, userId);
            bookmark.setTag("AI 관련");
            Page<BookmarkEntity> page = new PageImpl<>(List.of(bookmark));

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.searchBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("tag 필드만 검색")
        void searchBookmarks_tag검색() {
            // Given
            Long userId = 1L;
            BookmarkSearchRequest request = new BookmarkSearchRequest("ML", 1, 10, "tag");
            BookmarkEntity bookmark = createBookmark(1L, userId);
            bookmark.setTag("ML");
            Page<BookmarkEntity> page = new PageImpl<>(List.of(bookmark));

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.searchBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("title 필드만 검색")
        void searchBookmarks_title검색() {
            // Given
            Long userId = 1L;
            BookmarkSearchRequest request = new BookmarkSearchRequest("Test", 1, 10, "title");
            BookmarkEntity bookmark = createBookmark(1L, userId);
            bookmark.setTitle("Test Title");
            Page<BookmarkEntity> page = new PageImpl<>(List.of(bookmark));

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.searchBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("memo 필드만 검색")
        void searchBookmarks_memo검색() {
            // Given
            Long userId = 1L;
            BookmarkSearchRequest request = new BookmarkSearchRequest("important", 1, 10, "memo");
            BookmarkEntity bookmark = createBookmark(1L, userId);
            bookmark.setMemo("This is important");
            Page<BookmarkEntity> page = new PageImpl<>(List.of(bookmark));

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.searchBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("검색 결과 없음")
        void searchBookmarks_결과없음() {
            // Given
            Long userId = 1L;
            BookmarkSearchRequest request = new BookmarkSearchRequest("없는검색어", 1, 10, null);
            Page<BookmarkEntity> page = new PageImpl<>(List.of());

            when(bookmarkReaderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // When
            Page<BookmarkEntity> result = bookmarkQueryService.searchBookmarks(userId, request);

            // Then
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ========== 헬퍼 메서드 ==========

    private BookmarkEntity createBookmark(Long id, Long userId) {
        BookmarkEntity entity = new BookmarkEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setEmergingTechId(new ObjectId().toHexString());
        entity.setTitle("Test Title");
        entity.setUrl("https://example.com");
        entity.setProvider("test");
        entity.setTag("tag");
        entity.setMemo("memo");
        entity.setIsDeleted(false);
        return entity;
    }
}
