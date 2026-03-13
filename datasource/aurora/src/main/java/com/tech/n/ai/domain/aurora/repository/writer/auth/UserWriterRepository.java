package com.tech.n.ai.domain.aurora.repository.writer.auth;

import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.writer.BaseWriterRepository;
import com.tech.n.ai.domain.aurora.service.history.HistoryService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * UserWriterRepository
 */
@Service
@RequiredArgsConstructor
public class UserWriterRepository extends BaseWriterRepository<UserEntity> {

    private final UserWriterJpaRepository userWriterJpaRepository;
    private final HistoryService historyService;
    private final EntityManager entityManager;

    @Override
    protected JpaRepository<UserEntity, Long> getJpaRepository() {
        return userWriterJpaRepository;
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
    protected Class<UserEntity> getEntityClass() {
        return UserEntity.class;
    }

    @Override
    protected String getEntityName() {
        return "User";
    }
}
