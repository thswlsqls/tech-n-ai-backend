package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkListRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkSearchRequest;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import org.springframework.data.domain.Page;

/**
 * Bookmark Query Service 인터페이스
 */
public interface BookmarkQueryService {
    
    /**
     * 북마크 목록 조회
     * 
     * @param userId 사용자 ID
     * @param request BookmarkListRequest
     * @return 북마크 목록
     */
    Page<BookmarkEntity> findBookmarks(Long userId, BookmarkListRequest request);
    
    /**
     * 북마크 상세 조회
     * 
     * @param userId 사용자 ID
     * @param id 북마크 ID (TSID)
     * @return BookmarkEntity
     */
    BookmarkEntity findBookmarkById(Long userId, Long id);
    
    /**
     * 북마크 검색
     * 
     * @param userId 사용자 ID
     * @param request BookmarkSearchRequest
     * @return 검색 결과
     */
    Page<BookmarkEntity> searchBookmarks(Long userId, BookmarkSearchRequest request);
}
