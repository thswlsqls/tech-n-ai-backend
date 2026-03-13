package com.tech.n.ai.domain.aurora.repository.reader.auth;

import com.tech.n.ai.domain.aurora.entity.auth.AdminEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AdminReaderRepository
 */
@Repository
public interface AdminReaderRepository extends JpaRepository<AdminEntity, Long> {

    Optional<AdminEntity> findByEmail(String email);

    Optional<AdminEntity> findByUsername(String username);

    List<AdminEntity> findByIsActiveTrueAndIsDeletedFalse();

    Optional<AdminEntity> findByEmailAndIsActiveTrueAndIsDeletedFalse(String email);
}
