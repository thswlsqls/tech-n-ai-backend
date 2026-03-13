package com.tech.n.ai.domain.aurora.repository.reader.auth;

import com.tech.n.ai.domain.aurora.entity.auth.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EmailVerificationReaderRepository
 */
@Repository
public interface EmailVerificationReaderRepository extends JpaRepository<EmailVerificationEntity, Long> {
    
    /**
     * 토큰으로 조회
     * 
     * @param token 인증 토큰
     * @return EmailVerificationEntity (Optional)
     */
    Optional<EmailVerificationEntity> findByToken(String token);
    
    /**
     * 이메일로 조회
     * 
     * @param email 이메일
     * @return EmailVerificationEntity 목록
     */
    List<EmailVerificationEntity> findByEmail(String email);
    
    /**
     * 토큰과 타입으로 조회
     * 
     * @param token 인증 토큰
     * @param type 토큰 타입 (EMAIL_VERIFICATION, PASSWORD_RESET)
     * @return EmailVerificationEntity (Optional)
     */
    Optional<EmailVerificationEntity> findByTokenAndType(String token, String type);
    
    /**
     * 이메일과 타입으로 조회
     * 
     * @param email 이메일
     * @param type 토큰 타입 (EMAIL_VERIFICATION, PASSWORD_RESET)
     * @return EmailVerificationEntity 목록
     */
    List<EmailVerificationEntity> findByEmailAndType(String email, String type);
}
