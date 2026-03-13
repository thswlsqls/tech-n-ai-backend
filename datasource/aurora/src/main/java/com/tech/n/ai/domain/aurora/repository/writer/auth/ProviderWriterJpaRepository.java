package com.tech.n.ai.domain.aurora.repository.writer.auth;

import com.tech.n.ai.domain.aurora.entity.auth.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ProviderWriterJpaRepository
 */
@Repository
public interface ProviderWriterJpaRepository extends JpaRepository<ProviderEntity, Long> {
}
