package com.tech.n.ai.domain.aurora.repository.writer.auth;

import com.tech.n.ai.domain.aurora.entity.auth.AdminEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AdminWriterJpaRepository
 */
@Repository
public interface AdminWriterJpaRepository extends JpaRepository<AdminEntity, Long> {
}
