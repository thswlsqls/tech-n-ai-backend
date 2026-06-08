package com.tech.n.ai.api.bookmark.facade;

import com.tech.n.ai.api.bookmark.dto.request.*;
import com.tech.n.ai.api.bookmark.dto.response.*;
import com.tech.n.ai.api.bookmark.service.BookmarkCommandService;
import com.tech.n.ai.api.bookmark.service.BookmarkHistoryService;
import com.tech.n.ai.api.bookmark.service.BookmarkQueryService;
import com.tech.n.ai.common.core.dto.PageData;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Bookmark Facade
 * Controller와 Service 사이의 중간 계층
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkFacade {
    
    private final BookmarkCommandService bookmarkCommandService;
    private final BookmarkQueryService bookmarkQueryService;
    private final BookmarkHistoryService bookmarkHistoryService;
    private final BookmarkReaderRepository bookmarkReaderRepository;
    
    public BookmarkDetailResponse saveBookmark(Long userId, BookmarkCreateRequest request) {
        BookmarkEntity entity = bookmarkCommandService.saveBookmark(userId, request);
        return BookmarkDetailResponse.from(entity);
    }
    
    public BookmarkListResponse getBookmarkList(Long userId, BookmarkListRequest request) {
        Page<BookmarkEntity> page = bookmarkQueryService.findBookmarks(userId, request);

        return BookmarkListResponse.from(
            toPageData(page, request.page(), request.size(), BookmarkDetailResponse::from));
    }
    
    public BookmarkDetailResponse getBookmarkDetail(Long userId, String id) {
        Long bookmarkId = parseBookmarkId(id);
        BookmarkEntity entity = bookmarkQueryService.findBookmarkById(userId, bookmarkId);
        return BookmarkDetailResponse.from(entity);
    }
    
    public BookmarkDetailResponse updateBookmark(Long userId, String id, BookmarkUpdateRequest request) {
        bookmarkCommandService.updateBookmark(userId, id, request);
        Long bookmarkId = parseBookmarkId(id);
        BookmarkEntity entity = bookmarkQueryService.findBookmarkById(userId, bookmarkId);
        return BookmarkDetailResponse.from(entity);
    }
    
    public void deleteBookmark(Long userId, String id) {
        bookmarkCommandService.deleteBookmark(userId, id);
    }
    
    public BookmarkListResponse getDeletedBookmarks(Long userId, BookmarkDeletedListRequest request) {
        Pageable pageable = PageRequest.of(
            request.page() - 1,
            request.size(),
            Sort.by(Sort.Direction.DESC, "deletedAt")
        );
        
        Page<BookmarkEntity> page;
        
        if (request.days() != null && request.days() > 0) {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(request.days());
            page = bookmarkReaderRepository.findDeletedBookmarksWithinDays(userId, cutoffDate, pageable);
        } else {
            page = bookmarkReaderRepository.findByUserIdAndIsDeletedTrue(userId, pageable);
        }

        return BookmarkListResponse.from(
            toPageData(page, request.page(), request.size(), BookmarkDetailResponse::from));
    }
    
    public BookmarkDetailResponse restoreBookmark(Long userId, String id) {
        bookmarkCommandService.restoreBookmark(userId, id);
        Long bookmarkId = parseBookmarkId(id);
        BookmarkEntity entity = bookmarkQueryService.findBookmarkById(userId, bookmarkId);
        return BookmarkDetailResponse.from(entity);
    }
    
    public BookmarkSearchResponse searchBookmarks(Long userId, BookmarkSearchRequest request) {
        Page<BookmarkEntity> page = bookmarkQueryService.searchBookmarks(userId, request);

        return BookmarkSearchResponse.from(
            toPageData(page, request.page(), request.size(), BookmarkDetailResponse::from));
    }
    
    public BookmarkHistoryListResponse getHistory(Long userId, String entityId, BookmarkHistoryListRequest request) {
        Page<BookmarkHistoryEntity> page = bookmarkHistoryService.findHistory(userId.toString(), entityId, request);

        return BookmarkHistoryListResponse.from(
            toPageData(page, request.page(), request.size(), BookmarkHistoryDetailResponse::from));
    }
    
    public BookmarkHistoryDetailResponse getHistoryAt(Long userId, String entityId, String timestamp) {
        BookmarkHistoryEntity history = bookmarkHistoryService.findHistoryAt(userId.toString(), entityId, timestamp);
        return BookmarkHistoryDetailResponse.from(history);
    }
    
    public BookmarkDetailResponse restoreFromHistory(Long userId, String entityId, String historyId) {
        bookmarkHistoryService.restoreFromHistory(userId.toString(), entityId, historyId);
        Long bookmarkId = parseBookmarkId(entityId);
        BookmarkEntity entity = bookmarkQueryService.findBookmarkById(userId, bookmarkId);
        return BookmarkDetailResponse.from(entity);
    }

    private <E, D> PageData<D> toPageData(Page<E> page, int pageNumber, int size, Function<E, D> mapper) {
        List<D> list = page.getContent().stream()
            .map(mapper)
            .toList();
        return PageData.of(size, pageNumber, (int) page.getTotalElements(), list);
    }

    private Long parseBookmarkId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BookmarkValidationException("유효하지 않은 북마크 ID 형식입니다: " + id);
        }
    }
}
