package com.tech.n.ai.domain.aurora.repository.writer.auth;

import com.tech.n.ai.domain.aurora.entity.auth.EmailVerificationEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * EmailVerificationWriterRepository
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationWriterRepository {

    private final EmailVerificationWriterJpaRepository emailVerificationWriterJpaRepository;

    public EmailVerificationEntity save(EmailVerificationEntity entity) {
        return emailVerificationWriterJpaRepository.save(entity);
    }

    public EmailVerificationEntity saveAndFlush(EmailVerificationEntity entity) {
        return emailVerificationWriterJpaRepository.saveAndFlush(entity);
    }

    public void delete(EmailVerificationEntity entity) {
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        emailVerificationWriterJpaRepository.save(entity);
    }

    public void deleteById(Long id) {
        EmailVerificationEntity entity = emailVerificationWriterJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EmailVerification with id " + id + " does not exist"));
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        emailVerificationWriterJpaRepository.save(entity);
    }
}
