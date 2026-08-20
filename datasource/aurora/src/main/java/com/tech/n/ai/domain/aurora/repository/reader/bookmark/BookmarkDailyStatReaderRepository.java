package com.tech.n.ai.domain.aurora.repository.reader.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * BookmarkDailyStatReaderRepository
 */
@Repository
public interface BookmarkDailyStatReaderRepository extends JpaRepository<BookmarkDailyStatEntity, Long> {

    /**
     * 사용자·날짜·제공자로 집계 한 건 조회 (갱신 결과 확인용)
     */
    Optional<BookmarkDailyStatEntity> findByUserIdAndStatDateAndProviderAndIsDeletedFalse(
        Long userId, LocalDate statDate, String provider);

    /**
     * 구간 집계를 한 번에 읽는다.
     *
     * 날짜마다 따로 조회하면 90일 구간에서 SELECT 가 90번 나간다.
     * `(user_id, stat_date)` 인덱스가 이 범위 조회를 커버한다.
     */
    @Query("SELECT s FROM BookmarkDailyStatEntity s "
         + "WHERE s.userId = :userId "
         + "AND s.statDate BETWEEN :from AND :to "
         + "AND s.isDeleted = false "
         + "AND s.provider = :provider "
         + "ORDER BY s.statDate ASC, s.provider ASC")
    List<BookmarkDailyStatEntity> findRange(
        @Param("userId") Long userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("provider") String provider
    );
}
