package com.tech.n.ai.common.conversation.service;

import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.conversation.exception.ConversationSessionNotFoundException;
import com.tech.n.ai.common.conversation.exception.InvalidSessionIdException;
import com.tech.n.ai.common.conversation.memory.MongoDbChatMemoryStore;
import com.tech.n.ai.common.kafka.event.ConversationMessageCreatedEvent;
import com.tech.n.ai.common.kafka.publisher.EventPublisher;
import com.tech.n.ai.domain.aurora.entity.conversation.ConversationMessageEntity;
import com.tech.n.ai.domain.aurora.entity.conversation.ConversationSessionEntity;
import com.tech.n.ai.domain.aurora.repository.reader.conversation.ConversationSessionReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.conversation.ConversationMessageWriterRepository;
import com.tech.n.ai.domain.mongodb.document.ConversationMessageDocument;
import com.tech.n.ai.domain.mongodb.repository.ConversationMessageRepository;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 대화 메시지 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMessageServiceImpl implements ConversationMessageService {

    private static final String TOPIC_MESSAGE_CREATED = "tech-n-ai.conversation.message.created";

    private final ConversationMessageWriterRepository conversationMessageWriterRepository;
    private final com.tech.n.ai.domain.aurora.repository.writer.conversation.ConversationMessageWriterJpaRepository conversationMessageWriterJpaRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationSessionReaderRepository conversationSessionReaderRepository;
    private final MongoDbChatMemoryStore mongoDbChatMemoryStore;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public void saveMessage(String sessionId, String role, String content, Integer tokenCount) {
        Long sessionIdLong = parseSessionId(sessionId);

        // 세션 존재 확인
        ConversationSessionEntity session = conversationSessionReaderRepository.findById(sessionIdLong)
            .orElseThrow(() -> new ConversationSessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));

        // 다음 sequence number 계산
        int nextSequenceNumber = conversationMessageWriterJpaRepository
            .findTopBySessionIdOrderBySequenceNumberDesc(sessionIdLong)
            .map(m -> m.getSequenceNumber() + 1)
            .orElse(1);

        // 메시지 엔티티 생성 및 저장
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setSession(session);
        message.setRole(ConversationMessageEntity.MessageRole.valueOf(role));
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setSequenceNumber(nextSequenceNumber);
        message.setCreatedAt(LocalDateTime.now());

        ConversationMessageEntity savedMessage = conversationMessageWriterRepository.save(message);

        // Kafka 이벤트 발행
        ConversationMessageCreatedEvent.ConversationMessageCreatedPayload payload =
            new ConversationMessageCreatedEvent.ConversationMessageCreatedPayload(
                savedMessage.getMessageId().toString(),
                sessionId,
                role,
                content,
                tokenCount,
                nextSequenceNumber,
                savedMessage.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
            );

        ConversationMessageCreatedEvent event = new ConversationMessageCreatedEvent(payload);
        eventPublisher.publish(TOPIC_MESSAGE_CREATED, event, sessionId);

        log.info("Message saved: sessionId={}, role={}, sequenceNumber={}",
            sessionId, role, nextSequenceNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(String sessionId, Pageable pageable) {
        log.info("Fetching messages for sessionId={}, page={}, size={}",
            sessionId, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Page<ConversationMessageDocument> mongoPage = conversationMessageRepository
                .findBySessionIdOrderBySequenceNumberAsc(sessionId, pageable);

            if (mongoPage.hasContent()) {
                // 방어적 검증: 반환된 데이터의 sessionId가 요청한 것과 일치하는지 확인
                Page<MessageResponse> result = mongoPage.map(doc -> {
                    if (!sessionId.equals(doc.getSessionId())) {
                        log.error("Session ID mismatch detected! Requested: {}, Found: {}, MessageId: {}",
                            sessionId, doc.getSessionId(), doc.getMessageId());
                    }
                    return toResponse(doc);
                });

                log.info("Found {} messages from MongoDB for sessionId={}",
                    mongoPage.getTotalElements(), sessionId);
                return result;
            }
            log.info("No messages found in MongoDB for sessionId={}, falling back to Aurora MySQL", sessionId);
        } catch (Exception e) {
            log.warn("Failed to get messages from MongoDB, falling back to Aurora MySQL: sessionId={}",
                sessionId, e);
        }

        Long sessionIdLong = parseSessionId(sessionId);
        List<ConversationMessageEntity> messages = conversationMessageWriterJpaRepository
            .findBySessionIdOrderBySequenceNumberAsc(sessionIdLong);

        log.info("Found {} messages from Aurora MySQL for sessionId={}", messages.size(), sessionId);

        // 방어적 검증: Aurora MySQL 조회 결과의 sessionId가 요청한 것과 일치하는지 확인
        for (ConversationMessageEntity msg : messages) {
            Long actualSessionId = msg.getSession().getId();
            if (!sessionIdLong.equals(actualSessionId)) {
                log.error("Aurora MySQL Session ID mismatch! Requested: {}, Found: {}, MessageId: {}",
                    sessionId, actualSessionId, msg.getMessageId());
            }
        }

        int start = (int) pageable.getOffset();
        if (start >= messages.size()) {
            return new PageImpl<>(List.of(), pageable, messages.size());
        }
        int end = Math.min(start + pageable.getPageSize(), messages.size());
        List<ConversationMessageEntity> pagedMessages = messages.subList(start, end);

        List<MessageResponse> responses = pagedMessages.stream()
            .map(this::toResponseFromEntity)
            .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, messages.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesForMemory(String sessionId, Integer maxTokens) {
        // ChatMemory용 메시지 조회 (MongoDbChatMemoryStore 사용)
        List<ChatMessage> messages = mongoDbChatMemoryStore.getMessages(sessionId);

        // TODO: maxTokens 제한 적용 (TokenService 사용)
        // 현재는 모든 메시지 반환

        return messages;
    }

    private MessageResponse toResponse(ConversationMessageDocument doc) {
        return MessageResponse.builder()
            .messageId(doc.getMessageId())
            .sessionId(doc.getSessionId())
            .role(doc.getRole())
            .content(doc.getContent())
            .tokenCount(doc.getTokenCount())
            .sequenceNumber(doc.getSequenceNumber())
            .createdAt(doc.getCreatedAt())
            .build();
    }

    private MessageResponse toResponseFromEntity(ConversationMessageEntity entity) {
        return MessageResponse.builder()
            .messageId(entity.getMessageId().toString())
            .sessionId(entity.getSession().getId().toString())
            .role(entity.getRole().name())
            .content(entity.getContent())
            .tokenCount(entity.getTokenCount())
            .sequenceNumber(entity.getSequenceNumber())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private Long parseSessionId(String sessionId) {
        return InvalidSessionIdException.parseSessionId(sessionId);
    }
}
