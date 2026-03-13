package com.tech.n.ai.domain.aurora.repository.writer.auth;

import com.tech.n.ai.domain.aurora.entity.auth.ProviderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ProviderWriterRepository
 */
@Service
@RequiredArgsConstructor
public class ProviderWriterRepository {

    private final ProviderWriterJpaRepository providerWriterJpaRepository;

    public ProviderEntity save(ProviderEntity entity) {
        return providerWriterJpaRepository.save(entity);
    }

    public ProviderEntity saveAndFlush(ProviderEntity entity) {
        return providerWriterJpaRepository.saveAndFlush(entity);
    }

    public void delete(ProviderEntity entity) {
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        providerWriterJpaRepository.save(entity);
    }

    public void deleteById(Long id) {
        ProviderEntity entity = providerWriterJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Provider with id " + id + " does not exist"));
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        providerWriterJpaRepository.save(entity);
    }
}
