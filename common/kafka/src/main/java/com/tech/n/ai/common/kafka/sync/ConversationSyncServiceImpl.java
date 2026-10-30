package com.tech.n.ai.common.kafka.sync;

import com.tech.n.ai.common.kafka.event.ConversationMessageCreatedEvent;
import com.tech.n.ai.common.kafka.event.ConversationSessionCreatedEvent;
import com.tech.n.ai.common.kafka.event.ConversationSessionDeletedEvent;
import com.tech.n.ai.common.kafka.event.ConversationSessionUpdatedEvent;
import com.tech.n.ai.domain.mongodb.document.ConversationMessageDocument;
import com.tech.n.ai.domain.mongodb.document.ConversationSessionDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 대화 세션 및 메시지 동기화 서비스 구현 클래스
 * MongoDB Repository가 있을 때만 활성화됨
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(MongoTemplate.class)
public class ConversationSyncServiceImpl implements ConversationSyncService {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public void syncSessionCreated(ConversationSessionCreatedEvent event) {
        try {
            var payload = event.payload();

            // MongoDB atomic upsert: 동일 sessionId에 대해 여러 consumer group이 동시 처리해도 중복 생성 방지
            Query query = new Query(Criteria.where("session_id").is(payload.sessionId()));
            Update update = new Update()
                .set("session_id", payload.sessionId())
                .set("user_id", payload.userId())
                .set("title", payload.title())
                .set("last_message_at", convertToLocalDateTime(payload.lastMessageAt()))
                .set("is_active", payload.isActive())
                .set("updated_at", LocalDateTime.now())
                .setOnInsert("created_at", LocalDateTime.now());

            mongoTemplate.upsert(query, update, ConversationSessionDocument.class);

            log.debug("Successfully synced ConversationSessionCreatedEvent: sessionId={}, userId={}",
                payload.sessionId(), payload.userId());
        } catch (Exception e) {
            log.error("Failed to sync ConversationSessionCreatedEvent: eventId={}, sessionId={}",
                event.eventId(), event.payload().sessionId(), e);
            throw new RuntimeException("Failed to sync ConversationSessionCreatedEvent", e);
        }
    }
    
    @Override
    public void syncSessionUpdated(ConversationSessionUpdatedEvent event) {
        try {
            var payload = event.payload();
            var updatedFields = payload.updatedFields();

            // MongoDB atomic update: sessionId로 직접 업데이트 (중복 문서 문제 회피)
            Query query = new Query(Criteria.where("session_id").is(payload.sessionId()));
            Update update = buildSessionUpdate(updatedFields);
            update.set("updated_at", LocalDateTime.now());

            var result = mongoTemplate.updateFirst(query, update, ConversationSessionDocument.class);

            if (result.getMatchedCount() == 0) {
                log.warn("ConversationSessionDocument not found for update: sessionId={}, skipping",
                    payload.sessionId());
                return;
            }

            log.debug("Successfully synced ConversationSessionUpdatedEvent: sessionId={}, updatedFields={}",
                payload.sessionId(), updatedFields.keySet());
        } catch (Exception e) {
            log.error("Failed to sync ConversationSessionUpdatedEvent: eventId={}, sessionId={}",
                event.eventId(), event.payload().sessionId(), e);
            throw new RuntimeException("Failed to sync ConversationSessionUpdatedEvent", e);
        }
    }
    
    @Override
    public void syncSessionDeleted(ConversationSessionDeletedEvent event) {
        try {
            var payload = event.payload();

            // MongoDB는 Soft Delete를 지원하지 않으므로 물리적 삭제 (중복 문서도 모두 제거)
            Query query = new Query(Criteria.where("session_id").is(payload.sessionId()));
            mongoTemplate.remove(query, ConversationSessionDocument.class);

            log.debug("Successfully synced ConversationSessionDeletedEvent: sessionId={}, userId={}",
                payload.sessionId(), payload.userId());
        } catch (Exception e) {
            log.error("Failed to sync ConversationSessionDeletedEvent: eventId={}, sessionId={}",
                event.eventId(), event.payload().sessionId(), e);
            throw new RuntimeException("Failed to sync ConversationSessionDeletedEvent", e);
        }
    }
    
    @Override
    public void syncMessageCreated(ConversationMessageCreatedEvent event) {
        try {
            var payload = event.payload();

            // MongoDB atomic upsert: 동일 messageId에 대해 여러 consumer group이 동시 처리해도 중복 생성 방지
            Query query = new Query(Criteria.where("message_id").is(payload.messageId()));
            Update update = new Update()
                .set("message_id", payload.messageId())
                .set("session_id", payload.sessionId())
                .set("role", payload.role())
                .set("content", payload.content())
                .set("token_count", payload.tokenCount())
                .set("sequence_number", payload.sequenceNumber())
                .set("created_at", convertToLocalDateTime(payload.createdAt()));

            mongoTemplate.upsert(query, update, ConversationMessageDocument.class);

            log.debug("Successfully synced ConversationMessageCreatedEvent: messageId={}, sessionId={}",
                payload.messageId(), payload.sessionId());
        } catch (Exception e) {
            log.error("Failed to sync ConversationMessageCreatedEvent: eventId={}, messageId={}",
                event.eventId(), event.payload().messageId(), e);
            throw new RuntimeException("Failed to sync ConversationMessageCreatedEvent", e);
        }
    }
    
    /**
     * updatedFields를 MongoDB Update 객체로 변환
     */
    private Update buildSessionUpdate(Map<String, Object> updatedFields) {
        Update update = new Update();
        for (Map.Entry<String, Object> entry : updatedFields.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            try {
                switch (fieldName) {
                    case "title":
                        update.set("title", (String) value);
                        break;
                    case "lastMessageAt":
                        if (value instanceof Instant instant) {
                            update.set("last_message_at", convertToLocalDateTime(instant));
                        } else if (value instanceof String text) {
                            // 발행 측은 LocalDateTime을 넣고, Kafka JSON을 거치면 ISO 문자열로 도착한다
                            update.set("last_message_at", LocalDateTime.parse(text));
                        }
                        break;
                    case "isActive":
                        update.set("is_active", (Boolean) value);
                        break;
                    default:
                        log.warn("Unknown field in updatedFields: {}", fieldName);
                }
            } catch (ClassCastException e) {
                log.warn("Type mismatch for field {}: {}", fieldName, value.getClass().getName());
            }
        }
        return update;
    }
    
    /**
     * Instant를 LocalDateTime으로 변환
     */
    private LocalDateTime convertToLocalDateTime(Instant instant) {
        return instant != null 
            ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            : null;
    }
}
