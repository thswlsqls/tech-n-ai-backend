package com.tech.n.ai.domain.aurora.repository.writer.conversation;

import com.tech.n.ai.domain.aurora.entity.conversation.ConversationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ConversationSessionWriterJpaRepository
 */
@Repository
public interface ConversationSessionWriterJpaRepository extends JpaRepository<ConversationSessionEntity, Long> {

    List<ConversationSessionEntity> findByUserIdAndIsDeletedFalse(String userId);

    Optional<ConversationSessionEntity> findByUserIdAndIsActiveTrueAndIsDeletedFalse(String userId);

    /**
     * 비활성화 대상 세션 조회 (배치 작업용)
     * 활성 상태이지만 마지막 메시지 시간이 임계값 이전인 세션
     */
    List<ConversationSessionEntity> findByIsActiveTrueAndIsDeletedFalseAndLastMessageAtBefore(LocalDateTime thresholdTime);

    /**
     * 만료 대상 세션 조회 (배치 작업용)
     * 비활성 상태이고 마지막 메시지 시간이 만료 기간 이전인 세션
     */
    List<ConversationSessionEntity> findByIsActiveFalseAndIsDeletedFalseAndLastMessageAtBefore(LocalDateTime expirationTime);
}
