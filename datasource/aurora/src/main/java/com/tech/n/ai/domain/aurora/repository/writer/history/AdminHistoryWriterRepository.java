package com.tech.n.ai.domain.aurora.repository.writer.history;

import com.tech.n.ai.domain.aurora.entity.auth.AdminHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AdminHistoryWriterRepository
 */
@Service
@RequiredArgsConstructor
public class AdminHistoryWriterRepository {

    private final AdminHistoryWriterJpaRepository adminHistoryWriterJpaRepository;

    public AdminHistoryEntity save(AdminHistoryEntity entity) {
        return adminHistoryWriterJpaRepository.save(entity);
    }
}
