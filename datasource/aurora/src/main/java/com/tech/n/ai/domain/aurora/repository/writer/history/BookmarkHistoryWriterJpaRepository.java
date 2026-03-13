package com.tech.n.ai.domain.aurora.repository.writer.history;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * BookmarkHistoryWriterJpaRepository
 */
@Repository
public interface BookmarkHistoryWriterJpaRepository extends JpaRepository<BookmarkHistoryEntity, Long> {
}
