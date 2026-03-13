package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkCreateRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkUpdateRequest;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;

/**
 * Bookmark Command Service 인터페이스
 * Aurora MySQL을 사용하는 쓰기 작업을 처리합니다.
 */
public interface BookmarkCommandService {
    
    /**
     * 북마크 저장
     * 
     * @param userId 사용자 ID
     * @param request 북마크 생성 요청
     * @return 저장된 BookmarkEntity
     */
    BookmarkEntity saveBookmark(Long userId, BookmarkCreateRequest request);
    
    /**
     * 북마크 수정
     * 
     * @param userId 사용자 ID
     * @param bookmarkTsid 북마크 TSID
     * @param request 북마크 수정 요청
     * @return 수정된 BookmarkEntity
     */
    BookmarkEntity updateBookmark(Long userId, String bookmarkTsid, BookmarkUpdateRequest request);
    
    /**
     * 북마크 삭제 (Soft Delete)
     * 
     * @param userId 사용자 ID
     * @param bookmarkTsid 북마크 TSID
     */
    void deleteBookmark(Long userId, String bookmarkTsid);
    
    /**
     * 북마크 복구
     * 
     * @param userId 사용자 ID
     * @param bookmarkTsid 북마크 TSID
     * @return 복구된 BookmarkEntity
     */
    BookmarkEntity restoreBookmark(Long userId, String bookmarkTsid);
}
