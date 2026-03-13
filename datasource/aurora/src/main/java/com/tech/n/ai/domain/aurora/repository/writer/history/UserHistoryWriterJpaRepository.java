package com.tech.n.ai.domain.aurora.repository.writer.history;

import com.tech.n.ai.domain.aurora.entity.auth.UserHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * UserHistoryWriterJpaRepository
 */
@Repository
public interface UserHistoryWriterJpaRepository extends JpaRepository<UserHistoryEntity, Long> {
}
