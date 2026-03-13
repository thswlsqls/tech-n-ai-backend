package com.tech.n.ai.domain.aurora.service.history;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;

/**
 * History 엔티티 생성을 위한 Factory 인터페이스
 */
public interface HistoryEntityFactory {
    /**
     * History 엔티티를 생성하고 저장합니다.
     * 
     * @param entity 변경된 엔티티
     * @param operationType 작업 타입
     * @param beforeJson 변경 전 JSON 데이터
     * @param afterJson 변경 후 JSON 데이터
     * @param changedBy 변경한 사용자 ID
     * @param changedAt 변경 일시
     */
    void createAndSave(BaseEntity entity, OperationType operationType, 
                      String beforeJson, String afterJson, 
                      Long changedBy, java.time.LocalDateTime changedAt);
    
    /**
     * 이 Factory가 처리할 수 있는 엔티티 타입인지 확인합니다.
     * 
     * @param entity 엔티티
     * @return 처리 가능 여부
     */
    boolean supports(BaseEntity entity);
}
