package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkDuplicateException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkItemNotFoundException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkCreateRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkUpdateRequest;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkWriterRepository;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.repository.EmergingTechRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * BookmarkCommandService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkCommandService 단위 테스트")
class BookmarkCommandServiceTest {

    @Mock
    private BookmarkReaderRepository bookmarkReaderRepository;

    @Mock
    private BookmarkWriterRepository bookmarkWriterRepository;

    @Mock
    private EmergingTechRepository emergingTechRepository;

    @InjectMocks
    private BookmarkCommandServiceImpl bookmarkCommandService;

    // ========== saveBookmark 테스트 ==========

    @Nested
    @DisplayName("saveBookmark")
    class SaveBookmark {

        @Test
        @DisplayName("정상 저장 - BookmarkEntity 반환")
        void saveBookmark_성공() {
            // Given
            Long userId = 1L;
            String emergingTechId = new ObjectId().toHexString();
            BookmarkCreateRequest request = new BookmarkCreateRequest(emergingTechId, List.of("AI"), "메모");

            EmergingTechDocument emergingTech = createEmergingTech(emergingTechId);
            when(bookmarkReaderRepository.findByUserIdAndEmergingTechIdAndIsDeletedFalse(userId, emergingTechId))
                .thenReturn(Optional.empty());
            when(emergingTechRepository.findById(any(ObjectId.class))).thenReturn(Optional.of(emergingTech));
            when(bookmarkWriterRepository.save(any(BookmarkEntity.class))).thenAnswer(invocation -> {
                BookmarkEntity entity = invocation.getArgument(0);
                entity.setId(100L);
                return entity;
            });

            // When
            BookmarkEntity result = bookmarkCommandService.saveBookmark(userId, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getEmergingTechId()).isEqualTo(emergingTechId);
            assertThat(result.getTag()).isEqualTo("AI");
            verify(bookmarkWriterRepository).save(any(BookmarkEntity.class));
        }

        @Test
        @DisplayName("중복 북마크 시 BookmarkDuplicateException")
        void saveBookmark_중복() {
            // Given
            Long userId = 1L;
            String emergingTechId = new ObjectId().toHexString();
            BookmarkCreateRequest request = new BookmarkCreateRequest(emergingTechId, List.of("AI"), "메모");

            when(bookmarkReaderRepository.findByUserIdAndEmergingTechIdAndIsDeletedFalse(userId, emergingTechId))
                .thenReturn(Optional.of(new BookmarkEntity()));

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.saveBookmark(userId, request))
                .isInstanceOf(BookmarkDuplicateException.class);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("EmergingTech 미존재 시 BookmarkItemNotFoundException")
        void saveBookmark_EmergingTech_미존재() {
            // Given
            Long userId = 1L;
            String emergingTechId = new ObjectId().toHexString();
            BookmarkCreateRequest request = new BookmarkCreateRequest(emergingTechId, List.of("AI"), "메모");

            when(bookmarkReaderRepository.findByUserIdAndEmergingTechIdAndIsDeletedFalse(userId, emergingTechId))
                .thenReturn(Optional.empty());
            when(emergingTechRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.saveBookmark(userId, request))
                .isInstanceOf(BookmarkItemNotFoundException.class);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("유효하지 않은 EmergingTech ID 형식 시 BookmarkValidationException")
        void saveBookmark_잘못된_ID_형식() {
            // Given
            Long userId = 1L;
            String invalidId = "invalid-id";
            BookmarkCreateRequest request = new BookmarkCreateRequest(invalidId, List.of("AI"), "메모");

            when(bookmarkReaderRepository.findByUserIdAndEmergingTechIdAndIsDeletedFalse(userId, invalidId))
                .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.saveBookmark(userId, request))
                .isInstanceOf(BookmarkValidationException.class);
        }
    }

    // ========== updateBookmark 테스트 ==========

    @Nested
    @DisplayName("updateBookmark")
    class UpdateBookmark {

        @Test
        @DisplayName("정상 수정 - BookmarkEntity 반환")
        void updateBookmark_성공() {
            // Given
            Long userId = 1L;
            String bookmarkId = "100";
            BookmarkUpdateRequest request = new BookmarkUpdateRequest(List.of("새태그"), "새메모");
            BookmarkEntity bookmark = createBookmark(100L, userId);

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));
            when(bookmarkWriterRepository.save(any(BookmarkEntity.class))).thenReturn(bookmark);

            // When
            BookmarkEntity result = bookmarkCommandService.updateBookmark(userId, bookmarkId, request);

            // Then
            assertThat(result.getTag()).isEqualTo("새태그");
            assertThat(result.getMemo()).isEqualTo("새메모");
            verify(bookmarkWriterRepository).save(bookmark);
        }

        @Test
        @DisplayName("북마크 미존재 시 BookmarkNotFoundException")
        void updateBookmark_미존재() {
            // Given
            Long userId = 1L;
            String bookmarkId = "999";
            BookmarkUpdateRequest request = new BookmarkUpdateRequest(List.of("태그"), "메모");

            when(bookmarkReaderRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.updateBookmark(userId, bookmarkId, request))
                .isInstanceOf(BookmarkNotFoundException.class);
        }

        @Test
        @DisplayName("다른 사용자 북마크 수정 시 UnauthorizedException")
        void updateBookmark_권한없음() {
            // Given
            Long userId = 1L;
            Long otherUserId = 2L;
            String bookmarkId = "100";
            BookmarkUpdateRequest request = new BookmarkUpdateRequest(List.of("태그"), "메모");
            BookmarkEntity bookmark = createBookmark(100L, otherUserId);

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.updateBookmark(userId, bookmarkId, request))
                .isInstanceOf(UnauthorizedException.class);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("삭제된 북마크 수정 시 BookmarkNotFoundException")
        void updateBookmark_삭제됨() {
            // Given
            Long userId = 1L;
            String bookmarkId = "100";
            BookmarkUpdateRequest request = new BookmarkUpdateRequest(List.of("태그"), "메모");
            BookmarkEntity bookmark = createBookmark(100L, userId);
            bookmark.setIsDeleted(true);

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.updateBookmark(userId, bookmarkId, request))
                .isInstanceOf(BookmarkNotFoundException.class);
        }
    }

    // ========== deleteBookmark 테스트 ==========

    @Nested
    @DisplayName("deleteBookmark")
    class DeleteBookmark {

        @Test
        @DisplayName("정상 삭제")
        void deleteBookmark_성공() {
            // Given
            Long userId = 1L;
            String bookmarkId = "100";
            BookmarkEntity bookmark = createBookmark(100L, userId);

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));

            // When
            bookmarkCommandService.deleteBookmark(userId, bookmarkId);

            // Then
            verify(bookmarkWriterRepository).delete(bookmark);
        }

        @Test
        @DisplayName("다른 사용자 북마크 삭제 시 UnauthorizedException")
        void deleteBookmark_권한없음() {
            // Given
            Long userId = 1L;
            Long otherUserId = 2L;
            String bookmarkId = "100";
            BookmarkEntity bookmark = createBookmark(100L, otherUserId);

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.deleteBookmark(userId, bookmarkId))
                .isInstanceOf(UnauthorizedException.class);
            verify(bookmarkWriterRepository, never()).delete(any());
        }
    }

    // ========== restoreBookmark 테스트 ==========

    @Nested
    @DisplayName("restoreBookmark")
    class RestoreBookmark {

        @Test
        @DisplayName("정상 복구 - BookmarkEntity 반환")
        void restoreBookmark_성공() {
            // Given
            Long userId = 1L;
            String bookmarkId = "100";
            BookmarkEntity bookmark = createDeletedBookmark(100L, userId, LocalDateTime.now().minusDays(5));

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));
            when(bookmarkWriterRepository.save(any(BookmarkEntity.class))).thenReturn(bookmark);

            // When
            BookmarkEntity result = bookmarkCommandService.restoreBookmark(userId, bookmarkId);

            // Then
            assertThat(result.getIsDeleted()).isFalse();
            verify(bookmarkWriterRepository).save(bookmark);
        }

        @Test
        @DisplayName("복구 기간 초과 시 BookmarkValidationException")
        void restoreBookmark_기간초과() {
            // Given
            Long userId = 1L;
            String bookmarkId = "100";
            // 31일 전 삭제됨 (30일 제한 초과)
            BookmarkEntity bookmark = createDeletedBookmark(100L, userId, LocalDateTime.now().minusDays(31));

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.restoreBookmark(userId, bookmarkId))
                .isInstanceOf(BookmarkValidationException.class);
            verify(bookmarkWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("삭제되지 않은 북마크 복구 시 BookmarkValidationException")
        void restoreBookmark_삭제되지_않음() {
            // Given
            Long userId = 1L;
            String bookmarkId = "100";
            BookmarkEntity bookmark = createBookmark(100L, userId);

            when(bookmarkReaderRepository.findById(100L)).thenReturn(Optional.of(bookmark));

            // When & Then
            assertThatThrownBy(() -> bookmarkCommandService.restoreBookmark(userId, bookmarkId))
                .isInstanceOf(BookmarkValidationException.class);
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

    private BookmarkEntity createDeletedBookmark(Long id, Long userId, LocalDateTime deletedAt) {
        BookmarkEntity entity = createBookmark(id, userId);
        entity.setIsDeleted(true);
        entity.setDeletedAt(deletedAt);
        return entity;
    }

    private EmergingTechDocument createEmergingTech(String id) {
        EmergingTechDocument doc = new EmergingTechDocument();
        doc.setId(new ObjectId(id));
        doc.setTitle("Test EmergingTech");
        doc.setUrl("https://example.com");
        doc.setProvider("test");
        doc.setSummary("Summary");
        doc.setPublishedAt(LocalDateTime.now());
        return doc;
    }
}
