package com.tech.n.ai.common.kafka.sync;

import com.mongodb.client.result.UpdateResult;
import com.tech.n.ai.common.kafka.event.ConversationSessionUpdatedEvent;
import com.tech.n.ai.common.kafka.event.ConversationSessionUpdatedEvent.ConversationSessionUpdatedPayload;
import com.tech.n.ai.domain.mongodb.document.ConversationSessionDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationSyncServiceImplTest {

    private static final String SESSION_ID = "session-1";

    @Mock
    private MongoTemplate mongoTemplate;

    private ConversationSyncServiceImpl conversationSyncService;

    @BeforeEach
    void setUp() {
        conversationSyncService = new ConversationSyncServiceImpl(mongoTemplate);
    }

    @Nested
    @DisplayName("syncSessionUpdated")
    class SyncSessionUpdated {

        @Test
        @DisplayName("lastMessageAt이 Kafka JSON을 거친 ISO 문자열로 오면 last_message_at에 반영한다")
        void lastMessageAt_문자열() {
            // Given
            Map<String, Object> updatedFields = Map.of("lastMessageAt", "2026-10-30T11:42:19.123");
            givenMatchedCount(1L);

            // When
            conversationSyncService.syncSessionUpdated(eventOf(updatedFields));

            // Then
            Document set = capturedSet();
            assertThat(set.get("last_message_at"))
                .isEqualTo(LocalDateTime.of(2026, 10, 30, 11, 42, 19, 123_000_000));
        }

        @Test
        @DisplayName("lastMessageAt과 isActive가 함께 오면 둘 다 반영하고 updated_at도 설정한다")
        void lastMessageAt_isActive_함께() {
            // Given
            Map<String, Object> updatedFields = new LinkedHashMap<>();
            updatedFields.put("lastMessageAt", "2026-10-30T11:42:19.123");
            updatedFields.put("isActive", true);
            givenMatchedCount(1L);

            // When
            conversationSyncService.syncSessionUpdated(eventOf(updatedFields));

            // Then
            Document set = capturedSet();
            assertThat(set.get("last_message_at"))
                .isEqualTo(LocalDateTime.of(2026, 10, 30, 11, 42, 19, 123_000_000));
            assertThat(set.get("is_active")).isEqualTo(true);
            assertThat(set.get("updated_at")).isInstanceOf(LocalDateTime.class);
        }

        @Test
        @DisplayName("lastMessageAt이 Instant로 오면 지금처럼 시스템 기본 시간대의 LocalDateTime으로 반영한다")
        void lastMessageAt_Instant() {
            // Given
            Instant instant = Instant.parse("2026-10-30T02:42:19.123Z");
            Map<String, Object> updatedFields = Map.of("lastMessageAt", instant);
            givenMatchedCount(1L);

            // When
            conversationSyncService.syncSessionUpdated(eventOf(updatedFields));

            // Then
            Document set = capturedSet();
            assertThat(set.get("last_message_at"))
                .isEqualTo(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
        }

        @Test
        @DisplayName("갱신할 세션 문서가 없으면 예외 없이 건너뛴다")
        void 문서없음_건너뜀() {
            // Given
            Map<String, Object> updatedFields = Map.of("lastMessageAt", "2026-10-30T11:42:19.123");
            givenMatchedCount(0L);

            // When
            conversationSyncService.syncSessionUpdated(eventOf(updatedFields));

            // Then
            verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class),
                eq(ConversationSessionDocument.class));
        }
    }

    private void givenMatchedCount(long matchedCount) {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ConversationSessionDocument.class)))
            .thenReturn(UpdateResult.acknowledged(matchedCount, matchedCount, null));
    }

    private Document capturedSet() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), captor.capture(), eq(ConversationSessionDocument.class));
        return (Document) captor.getValue().getUpdateObject().get("$set");
    }

    private ConversationSessionUpdatedEvent eventOf(Map<String, Object> updatedFields) {
        return new ConversationSessionUpdatedEvent(
            new ConversationSessionUpdatedPayload(SESSION_ID, "user-1", updatedFields));
    }
}
