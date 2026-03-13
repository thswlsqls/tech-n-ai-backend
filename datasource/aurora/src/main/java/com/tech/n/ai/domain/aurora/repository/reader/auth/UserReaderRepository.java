package com.tech.n.ai.domain.aurora.repository.reader.auth;

import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserReaderRepository extends JpaRepository<UserEntity, Long> {
    
    Optional<UserEntity> findByEmail(String email);
    
    Optional<UserEntity> findByUsername(String username);
    
    Optional<UserEntity> findByProviderIdAndProviderUserId(Long providerId, String providerUserId);
}
