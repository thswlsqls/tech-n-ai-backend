package com.tech.n.ai.domain.aurora.repository.reader.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * BookmarkHistoryReaderRepository
 */
@Repository
public interface BookmarkHistoryReaderRepository extends JpaRepository<BookmarkHistoryEntity, Long> {
    
    /**
     * bookmarkId로 히스토리 조회 (페이징)
     * 
     * @param bookmarkId 북마크 ID
     * @param pageable 페이징 정보
     * @return BookmarkHistoryEntity 페이지
     */
    Page<BookmarkHistoryEntity> findByBookmarkId(Long bookmarkId, Pageable pageable);
    
    /**
     * bookmarkId와 operationType으로 히스토리 조회 (페이징)
     * 
     * @param bookmarkId 북마크 ID
     * @param operationType 작업 타입
     * @param pageable 페이징 정보
     * @return BookmarkHistoryEntity 페이지
     */
    Page<BookmarkHistoryEntity> findByBookmarkIdAndOperationType(Long bookmarkId, String operationType, Pageable pageable);
    
    /**
     * bookmarkId와 날짜 범위로 히스토리 조회 (페이징)
     * 
     * @param bookmarkId 북마크 ID
     * @param startDate 시작 일시
     * @param endDate 종료 일시
     * @param pageable 페이징 정보
     * @return BookmarkHistoryEntity 페이지
     */
    @Query("SELECT h FROM BookmarkHistoryEntity h WHERE h.bookmarkId = :bookmarkId " +
           "AND h.changedAt >= :startDate AND h.changedAt <= :endDate")
    Page<BookmarkHistoryEntity> findByBookmarkIdAndChangedAtBetween(
        @Param("bookmarkId") Long bookmarkId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    /**
     * 특정 시점 이전의 가장 최근 히스토리 조회
     * 
     * @param bookmarkId 북마크 ID
     * @param timestamp 시점
     * @return BookmarkHistoryEntity (Optional)
     */
    @Query("SELECT h FROM BookmarkHistoryEntity h WHERE h.bookmarkId = :bookmarkId " +
           "AND h.changedAt <= :timestamp ORDER BY h.changedAt DESC")
    List<BookmarkHistoryEntity> findTop1ByBookmarkIdAndChangedAtLessThanEqualOrderByChangedAtDesc(
        @Param("bookmarkId") Long bookmarkId,
        @Param("timestamp") LocalDateTime timestamp
    );
    
    /**
     * historyId로 히스토리 조회
     * 
     * @param historyId 히스토리 ID
     * @return BookmarkHistoryEntity (Optional)
     */
    Optional<BookmarkHistoryEntity> findByHistoryId(Long historyId);
}
