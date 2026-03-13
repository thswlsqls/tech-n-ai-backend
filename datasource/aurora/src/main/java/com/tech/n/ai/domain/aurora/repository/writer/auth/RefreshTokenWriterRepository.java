package com.tech.n.ai.domain.aurora.repository.writer.auth;

import com.tech.n.ai.domain.aurora.entity.auth.RefreshTokenEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * RefreshTokenWriterRepository
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenWriterRepository {

    private final RefreshTokenWriterJpaRepository refreshTokenWriterJpaRepository;

    public RefreshTokenEntity save(RefreshTokenEntity entity) {
        return refreshTokenWriterJpaRepository.save(entity);
    }

    public RefreshTokenEntity saveAndFlush(RefreshTokenEntity entity) {
        return refreshTokenWriterJpaRepository.saveAndFlush(entity);
    }

    public void delete(RefreshTokenEntity entity) {
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        refreshTokenWriterJpaRepository.save(entity);
    }

    public void deleteById(Long id) {
        RefreshTokenEntity entity = refreshTokenWriterJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("RefreshToken with id " + id + " does not exist"));
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        refreshTokenWriterJpaRepository.save(entity);
    }
}
