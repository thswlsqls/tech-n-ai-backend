package com.tech.n.ai.domain.mariadb.repository.reader.auth;

import com.tech.n.ai.domain.mariadb.entity.auth.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RefreshTokenReaderRepository
 */
@Repository
public interface RefreshTokenReaderRepository extends JpaRepository<RefreshTokenEntity, Long> {
    
    /**
     * 토큰으로 조회
     * 
     * @param token Refresh Token
     * @return RefreshTokenEntity (Optional)
     */
    Optional<RefreshTokenEntity> findByToken(String token);
    
    /**
     * 사용자 ID로 조회
     *
     * @param userId 사용자 ID
     * @return RefreshTokenEntity 목록
     */
    List<RefreshTokenEntity> findByUserId(Long userId);

    /**
     * 관리자 ID로 조회
     *
     * @param adminId 관리자 ID
     * @return RefreshTokenEntity 목록
     */
    List<RefreshTokenEntity> findByAdminId(Long adminId);

    /**
     * 관리자 ID로 활성 토큰 조회 (soft-delete 제외)
     *
     * @param adminId 관리자 ID
     * @return 활성 RefreshTokenEntity 목록
     */
    List<RefreshTokenEntity> findByAdminIdAndIsDeletedFalse(Long adminId);
}
