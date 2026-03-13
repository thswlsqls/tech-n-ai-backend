package com.tech.n.ai.domain.aurora.repository.writer.conversation;

import com.tech.n.ai.domain.aurora.entity.conversation.ConversationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ConversationMessageWriterJpaRepository
 */
@Repository
public interface ConversationMessageWriterJpaRepository extends JpaRepository<ConversationMessageEntity, Long> {

    List<ConversationMessageEntity> findBySessionIdOrderBySequenceNumberAsc(Long sessionId);

    Optional<ConversationMessageEntity> findTopBySessionIdOrderBySequenceNumberDesc(Long sessionId);
}
