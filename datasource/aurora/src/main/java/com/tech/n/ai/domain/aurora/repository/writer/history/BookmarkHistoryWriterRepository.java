package com.tech.n.ai.domain.aurora.repository.writer.history;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * BookmarkHistoryWriterRepository
 */
@Service
@RequiredArgsConstructor
public class BookmarkHistoryWriterRepository {

    private final BookmarkHistoryWriterJpaRepository bookmarkHistoryWriterJpaRepository;

    public BookmarkHistoryEntity save(BookmarkHistoryEntity entity) {
        return bookmarkHistoryWriterJpaRepository.save(entity);
    }
}
