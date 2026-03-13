package com.tech.n.ai.domain.aurora.repository.writer.history;

import com.tech.n.ai.domain.aurora.entity.auth.UserHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * UserHistoryWriterRepository
 */
@Service
@RequiredArgsConstructor
public class UserHistoryWriterRepository {

    private final UserHistoryWriterJpaRepository userHistoryWriterJpaRepository;

    public UserHistoryEntity save(UserHistoryEntity entity) {
        return userHistoryWriterJpaRepository.save(entity);
    }
}
