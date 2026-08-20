package com.tech.n.ai.domain.aurora.repository.writer.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * BookmarkDailyStatWriterJpaRepository
 */
@Repository
public interface BookmarkDailyStatWriterJpaRepository extends JpaRepository<BookmarkDailyStatEntity, Long> {
}
