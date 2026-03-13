package com.tech.n.ai.domain.aurora.repository.reader.auth;

import com.tech.n.ai.domain.aurora.entity.auth.UserHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * UserHistoryReaderRepository
 */
@Repository
public interface UserHistoryReaderRepository extends JpaRepository<UserHistoryEntity, Long> {
    
    /**
     * 사용자 ID로 조회
     * 
     * @param userId 사용자 ID
     * @return UserHistoryEntity 목록
     */
    List<UserHistoryEntity> findByUserId(Long userId);
    
    /**
     * 사용자 ID와 작업 타입으로 조회
     * 
     * @param userId 사용자 ID
     * @param operationType 작업 타입 (INSERT, UPDATE, DELETE)
     * @return UserHistoryEntity 목록
     */
    List<UserHistoryEntity> findByUserIdAndOperationType(Long userId, String operationType);
}
