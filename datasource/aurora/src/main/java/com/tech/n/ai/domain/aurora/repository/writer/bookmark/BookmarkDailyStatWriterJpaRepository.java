package com.tech.n.ai.domain.aurora.repository.writer.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * BookmarkDailyStatWriterJpaRepository
 */
@Repository
public interface BookmarkDailyStatWriterJpaRepository extends JpaRepository<BookmarkDailyStatEntity, Long> {

    /**
     * 조회 수를 한 문장으로 올린다.
     *
     * 읽어서 더한 뒤 다시 쓰면 인스턴스 두 대가 같은 순간에 처리할 때 한쪽 증가분이 사라진다.
     * 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 `clearAutomatically` 로 1차 캐시를 비운다.
     *
     * `flushAutomatically` 는 그 짝이라 같이 켜야 한다. 이 UPDATE 가 건드리는 테이블은
     * bookmark_daily_stats 뿐이어서, 다른 테이블에 아직 flush 되지 않은 INSERT 가 있어도
     * Hibernate 의 auto-flush 가 돌지 않는다. 그 상태로 비우면 그 INSERT 가 통째로 사라진다.
     *
     * @return 갱신된 행 수. 0이면 그 날짜 행이 아직 없다는 뜻이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE BookmarkDailyStatEntity s SET s.viewCount = s.viewCount + 1 "
         + "WHERE s.userId = :userId AND s.statDate = :statDate AND s.isDeleted = false "
         + "AND (s.provider = :provider OR (:provider IS NULL AND s.provider IS NULL))")
    int increaseViewCount(
        @Param("userId") Long userId,
        @Param("statDate") LocalDate statDate,
        @Param("provider") String provider
    );
}
