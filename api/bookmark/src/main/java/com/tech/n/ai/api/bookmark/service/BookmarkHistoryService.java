package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkHistoryListRequest;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import org.springframework.data.domain.Page;

/**
 * Bookmark History Service 인터페이스
 */
public interface BookmarkHistoryService {
    
    /**
     * 변경 이력 조회
     * 
     * @param userId 사용자 ID
     * @param entityId 북마크 엔티티 ID (TSID)
     * @param request BookmarkHistoryListRequest
     * @return 변경 이력 목록
     */
    Page<BookmarkHistoryEntity> findHistory(String userId, String entityId, BookmarkHistoryListRequest request);
    
    /**
     * 특정 시점 데이터 조회
     * 
     * @param userId 사용자 ID
     * @param entityId 북마크 엔티티 ID (TSID)
     * @param timestamp 시점 (ISO 8601)
     * @return 특정 시점의 히스토리 엔티티
     */
    BookmarkHistoryEntity findHistoryAt(String userId, String entityId, String timestamp);
    
    /**
     * 특정 버전으로 복구
     * 
     * @param userId 사용자 ID
     * @param entityId 북마크 엔티티 ID (TSID)
     * @param historyId 히스토리 ID (TSID)
     * @return 복구된 BookmarkEntity
     */
    BookmarkEntity restoreFromHistory(String userId, String entityId, String historyId);
}
