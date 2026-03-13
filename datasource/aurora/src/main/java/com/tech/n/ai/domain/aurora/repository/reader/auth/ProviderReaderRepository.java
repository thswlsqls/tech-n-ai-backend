package com.tech.n.ai.domain.aurora.repository.reader.auth;

import com.tech.n.ai.domain.aurora.entity.auth.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ProviderReaderRepository
 */
@Repository
public interface ProviderReaderRepository extends JpaRepository<ProviderEntity, Long> {
    
    /**
     * 제공자 이름으로 조회
     * 
     * @param name 제공자 이름
     * @return ProviderEntity (Optional)
     */
    Optional<ProviderEntity> findByName(String name);
    
    /**
     * 활성화 여부로 조회
     * 
     * @param isEnabled 활성화 여부
     * @return ProviderEntity 목록
     */
    java.util.List<ProviderEntity> findByIsEnabled(Boolean isEnabled);
}
