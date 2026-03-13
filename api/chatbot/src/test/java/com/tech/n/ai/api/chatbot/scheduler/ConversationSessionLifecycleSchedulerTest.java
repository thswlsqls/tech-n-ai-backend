package com.tech.n.ai.api.chatbot.scheduler;

import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * ConversationSessionLifecycleScheduler 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationSessionLifecycleScheduler 단위 테스트")
class ConversationSessionLifecycleSchedulerTest {

    @Mock
    private ConversationSessionService conversationSessionService;

    @InjectMocks
    private ConversationSessionLifecycleScheduler scheduler;

    // ========== deactivateInactiveSessions 테스트 ==========

    @Nested
    @DisplayName("deactivateInactiveSessions")
    class DeactivateInactiveSessions {

        @Test
        @DisplayName("정상 실행 - 비활성화 건수 반환")
        void deactivateInactiveSessions_정상() {
            // Given
            ReflectionTestUtils.setField(scheduler, "inactiveThresholdMinutes", 30);
            when(conversationSessionService.deactivateInactiveSessions(Duration.ofMinutes(30)))
                .thenReturn(5);

            // When
            scheduler.deactivateInactiveSessions();

            // Then
            verify(conversationSessionService).deactivateInactiveSessions(Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("비활성화 대상 없음 - 0건")
        void deactivateInactiveSessions_대상없음() {
            // Given
            ReflectionTestUtils.setField(scheduler, "inactiveThresholdMinutes", 30);
            when(conversationSessionService.deactivateInactiveSessions(Duration.ofMinutes(30)))
                .thenReturn(0);

            // When
            scheduler.deactivateInactiveSessions();

            // Then
            verify(conversationSessionService).deactivateInactiveSessions(Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("예외 발생 시 에러 로깅만 수행 (예외 전파 안함)")
        void deactivateInactiveSessions_예외() {
            // Given
            ReflectionTestUtils.setField(scheduler, "inactiveThresholdMinutes", 30);
            when(conversationSessionService.deactivateInactiveSessions(Duration.ofMinutes(30)))
                .thenThrow(new RuntimeException("DB 연결 실패"));

            // When - 예외가 전파되지 않음
            scheduler.deactivateInactiveSessions();

            // Then
            verify(conversationSessionService).deactivateInactiveSessions(Duration.ofMinutes(30));
        }
    }

    // ========== expireInactiveSessions 테스트 ==========

    @Nested
    @DisplayName("expireInactiveSessions")
    class ExpireInactiveSessions {

        @Test
        @DisplayName("정상 실행 - 만료 처리 건수 반환")
        void expireInactiveSessions_정상() {
            // Given
            ReflectionTestUtils.setField(scheduler, "expirationDays", 90);
            when(conversationSessionService.expireInactiveSessions(90)).thenReturn(3);

            // When
            scheduler.expireInactiveSessions();

            // Then
            verify(conversationSessionService).expireInactiveSessions(90);
        }

        @Test
        @DisplayName("만료 대상 없음 - 0건")
        void expireInactiveSessions_대상없음() {
            // Given
            ReflectionTestUtils.setField(scheduler, "expirationDays", 90);
            when(conversationSessionService.expireInactiveSessions(90)).thenReturn(0);

            // When
            scheduler.expireInactiveSessions();

            // Then
            verify(conversationSessionService).expireInactiveSessions(90);
        }

        @Test
        @DisplayName("예외 발생 시 에러 로깅만 수행 (예외 전파 안함)")
        void expireInactiveSessions_예외() {
            // Given
            ReflectionTestUtils.setField(scheduler, "expirationDays", 90);
            when(conversationSessionService.expireInactiveSessions(anyInt()))
                .thenThrow(new RuntimeException("DB 연결 실패"));

            // When - 예외가 전파되지 않음
            scheduler.expireInactiveSessions();

            // Then
            verify(conversationSessionService).expireInactiveSessions(90);
        }
    }
}
