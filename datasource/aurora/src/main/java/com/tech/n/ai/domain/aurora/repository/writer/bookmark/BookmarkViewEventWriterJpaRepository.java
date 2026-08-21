package com.tech.n.ai.domain.aurora.repository.writer.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkViewEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * BookmarkViewEventWriterJpaRepository
 */
@Repository
public interface BookmarkViewEventWriterJpaRepository extends JpaRepository<BookmarkViewEventEntity, Long> {
}
