package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.domain.aurora.entity.auth.AdminEntity;
import com.tech.n.ai.domain.aurora.entity.auth.RefreshTokenEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.AdminReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.auth.RefreshTokenReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.RefreshTokenWriterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenWriterRepository refreshTokenWriterRepository;
    private final RefreshTokenReaderRepository refreshTokenReaderRepository;
    private final UserReaderRepository userReaderRepository;
    private final AdminReaderRepository adminReaderRepository;

    @Transactional
    public RefreshTokenEntity saveRefreshToken(Long userId, String token, LocalDateTime expiresAt) {
        UserEntity user = userReaderRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.createForUser(userId, token, expiresAt);
        refreshTokenEntity.setUser(user);
        return refreshTokenWriterRepository.save(refreshTokenEntity);
    }

    @Transactional
    public RefreshTokenEntity saveAdminRefreshToken(Long adminId, String token, LocalDateTime expiresAt) {
        AdminEntity admin = adminReaderRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found with id: " + adminId));

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.createForAdmin(adminId, token, expiresAt);
        refreshTokenEntity.setAdmin(admin);
        return refreshTokenWriterRepository.save(refreshTokenEntity);
    }
    
    public Optional<RefreshTokenEntity> findRefreshToken(String token) {
        return refreshTokenReaderRepository.findByToken(token);
    }
    
    @Transactional
    public void deleteRefreshToken(RefreshTokenEntity refreshTokenEntity) {
        refreshTokenWriterRepository.delete(refreshTokenEntity);
    }
    
    public boolean validateRefreshToken(String token) {
        return findRefreshToken(token)
            .filter(entity -> !Boolean.TRUE.equals(entity.getIsDeleted()))
            .filter(entity -> entity.getExpiresAt().isAfter(LocalDateTime.now()))
            .isPresent();
    }

    @Transactional
    public void deleteAllAdminRefreshTokens(Long adminId) {
        List<RefreshTokenEntity> tokens = refreshTokenReaderRepository.findByAdminIdAndIsDeletedFalse(adminId);
        for (RefreshTokenEntity token : tokens) {
            refreshTokenWriterRepository.delete(token);
        }
    }
}
