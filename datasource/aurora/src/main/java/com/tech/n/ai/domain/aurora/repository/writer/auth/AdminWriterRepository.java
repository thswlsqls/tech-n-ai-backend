package com.tech.n.ai.domain.aurora.repository.writer.auth;

import com.tech.n.ai.domain.aurora.entity.auth.AdminEntity;
import com.tech.n.ai.domain.aurora.repository.writer.BaseWriterRepository;
import com.tech.n.ai.domain.aurora.service.history.HistoryService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * AdminWriterRepository
 */
@Service
@RequiredArgsConstructor
public class AdminWriterRepository extends BaseWriterRepository<AdminEntity> {

    private final AdminWriterJpaRepository adminWriterJpaRepository;
    private final HistoryService historyService;
    private final EntityManager entityManager;

    @Override
    protected JpaRepository<AdminEntity, Long> getJpaRepository() {
        return adminWriterJpaRepository;
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
    protected Class<AdminEntity> getEntityClass() {
        return AdminEntity.class;
    }

    @Override
    protected String getEntityName() {
        return "Admin";
    }
}
