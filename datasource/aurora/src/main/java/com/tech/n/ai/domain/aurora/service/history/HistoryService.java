package com.tech.n.ai.domain.aurora.service.history;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;

/**
 * HistoryService
 */
public interface HistoryService {
    /**
     * 엔티티 변경 이력을 저장합니다.
     * 
     * @param entity 변경된 엔티티 (BaseEntity를 상속한 엔티티)
     * @param operationType 작업 타입
     * @param beforeData 변경 전 데이터 (JSON 직렬화 대상)
     * @param afterData 변경 후 데이터 (JSON 직렬화 대상)
     * @throws IllegalArgumentException entity가 null이거나 지원하지 않는 타입인 경우
     * @throws RuntimeException JSON 직렬화 실패 또는 History 저장 실패 시
     */
    void saveHistory(BaseEntity entity, OperationType operationType, Object beforeData, Object afterData);
}
