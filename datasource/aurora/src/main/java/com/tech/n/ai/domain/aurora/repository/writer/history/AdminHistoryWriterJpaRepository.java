package com.tech.n.ai.domain.aurora.repository.writer.history;

import com.tech.n.ai.domain.aurora.entity.auth.AdminHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AdminHistoryWriterJpaRepository
 */
@Repository
public interface AdminHistoryWriterJpaRepository extends JpaRepository<AdminHistoryEntity, Long> {
}
