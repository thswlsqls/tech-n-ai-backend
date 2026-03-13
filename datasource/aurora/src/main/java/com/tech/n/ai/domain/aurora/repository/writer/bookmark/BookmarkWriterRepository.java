package com.tech.n.ai.domain.aurora.repository.writer.bookmark;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.repository.writer.BaseWriterRepository;
import com.tech.n.ai.domain.aurora.service.history.HistoryService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * BookmarkWriterRepository
 */
@Service
@RequiredArgsConstructor
public class BookmarkWriterRepository extends BaseWriterRepository<BookmarkEntity> {

    private final BookmarkWriterJpaRepository bookmarkWriterJpaRepository;
    private final HistoryService historyService;
    private final EntityManager entityManager;

    @Override
    protected JpaRepository<BookmarkEntity, Long> getJpaRepository() {
        return bookmarkWriterJpaRepository;
    }

    @Override
    protected HistoryService getHistoryService() {
        return historyService;
    }

    @Override
    protected EntityManager getEntityManager() {
        return entityManager;
    }

    @Override
    protected Class<BookmarkEntity> getEntityClass() {
        return BookmarkEntity.class;
    }

    @Override
    protected String getEntityName() {
        return "Bookmark";
    }
}
