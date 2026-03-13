package com.tech.n.ai.domain.aurora.repository.writer.conversation;

import com.tech.n.ai.domain.aurora.entity.conversation.ConversationSessionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ConversationSessionWriterRepository
 */
@Service
@RequiredArgsConstructor
public class ConversationSessionWriterRepository {

    private final ConversationSessionWriterJpaRepository conversationSessionWriterJpaRepository;

    public ConversationSessionEntity save(ConversationSessionEntity entity) {
        return conversationSessionWriterJpaRepository.save(entity);
    }

    public ConversationSessionEntity saveAndFlush(ConversationSessionEntity entity) {
        return conversationSessionWriterJpaRepository.saveAndFlush(entity);
    }

    public void delete(ConversationSessionEntity entity) {
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        conversationSessionWriterJpaRepository.save(entity);
    }
}
