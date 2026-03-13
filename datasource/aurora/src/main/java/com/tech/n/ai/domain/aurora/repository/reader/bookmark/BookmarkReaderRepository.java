package com.tech.n.ai.domain.aurora.repository.reader.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * BookmarkReaderRepository
 */
@Repository
public interface BookmarkReaderRepository extends JpaRepository<BookmarkEntity, Long>, JpaSpecificationExecutor<BookmarkEntity> {
    
    /**
     * 중복 검증 (userId + emergingTechId)
     */
    Optional<BookmarkEntity> findByUserIdAndEmergingTechIdAndIsDeletedFalse(Long userId, String emergingTechId);
    
    /**
     * 삭제된 북마크 조회 (페이징)
     * 
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 삭제된 BookmarkEntity 페이지
     */
    Page<BookmarkEntity> findByUserIdAndIsDeletedTrue(Long userId, Pageable pageable);
    
    /**
     * 삭제된 북마크 조회 (복구 가능 기간 필터링)
     * 
     * @param userId 사용자 ID
     * @param days 복구 가능 기간 (일)
     * @param pageable 페이징 정보
     * @return 삭제된 BookmarkEntity 페이지
     */
    @Query("SELECT a FROM BookmarkEntity a WHERE a.userId = :userId AND a.isDeleted = true " +
           "AND a.deletedAt >= :cutoffDate")
    Page<BookmarkEntity> findDeletedBookmarksWithinDays(
        @Param("userId") Long userId,
        @Param("cutoffDate") LocalDateTime cutoffDate,
        Pageable pageable
    );
}
