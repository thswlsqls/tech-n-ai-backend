package com.tech.n.ai.domain.aurora.repository.reader.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * 하루치 집계 조회
     */
    List<BookmarkDailyStatEntity> findByUserIdAndStatDate(Long userId, LocalDate statDate);

    /**
     * 사용자·날짜·제공자로 집계 한 건 조회 (갱신 대상 확인용)
     */
    Optional<BookmarkDailyStatEntity> findByUserIdAndStatDateAndProvider(Long userId, LocalDate statDate, String provider);
}
