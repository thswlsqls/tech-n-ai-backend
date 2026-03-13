package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.exception.ConversationSessionNotFoundException;
import com.tech.n.ai.common.conversation.exception.InvalidSessionIdException;
import com.tech.n.ai.common.conversation.service.ConversationSessionServiceImpl;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.common.kafka.publisher.EventPublisher;
import com.tech.n.ai.domain.aurora.entity.conversation.ConversationSessionEntity;
import com.tech.n.ai.domain.aurora.repository.reader.conversation.ConversationSessionReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.conversation.ConversationSessionWriterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ConversationSessionService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationSessionService 단위 테스트")
class ConversationSessionServiceTest {

    @Mock
    private ConversationSessionWriterRepository writerRepository;

    @Mock
    private ConversationSessionReaderRepository readerRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ConversationSessionServiceImpl sessionService;

    private static final String TEST_USER_ID = "1";
    private static final Long TEST_SESSION_ID = 100L;

    // ========== createSession 테스트 ==========

    @Nested
    @DisplayName("createSession")
    class CreateSession {

        @Test
        @DisplayName("정상 생성 - 세션 ID 반환")
        void createSession_성공() {
            // Given
            ConversationSessionEntity savedSession = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(writerRepository.save(any(ConversationSessionEntity.class))).thenReturn(savedSession);

            // When
            String result = sessionService.createSession(TEST_USER_ID, "테스트 대화");

            // Then
            assertThat(result).isEqualTo(TEST_SESSION_ID.toString());
            verify(writerRepository).save(any(ConversationSessionEntity.class));
            verify(eventPublisher).publish(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("title이 null이어도 정상 생성")
        void createSession_nullTitle() {
            // Given
            ConversationSessionEntity savedSession = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(writerRepository.save(any(ConversationSessionEntity.class))).thenReturn(savedSession);

            // When
            String result = sessionService.createSession(TEST_USER_ID, null);

            // Then
            assertThat(result).isNotNull();
        }
    }

    // ========== getSession 테스트 ==========

    @Nested
    @DisplayName("getSession")
    class GetSession {

        @Test
        @DisplayName("정상 조회 - SessionResponse 반환")
        void getSession_성공() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));

            // When
            SessionResponse result = sessionService.getSession(TEST_SESSION_ID.toString(), TEST_USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.sessionId()).isEqualTo(TEST_SESSION_ID.toString());
        }

        @Test
        @DisplayName("세션 미존재 시 ConversationSessionNotFoundException")
        void getSession_미존재() {
            // Given
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> sessionService.getSession(TEST_SESSION_ID.toString(), TEST_USER_ID))
                .isInstanceOf(ConversationSessionNotFoundException.class)
                .hasMessageContaining("세션을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("다른 사용자 세션 접근 시 UnauthorizedException")
        void getSession_권한없음() {
            // Given
            String otherUserId = "2";
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));

            // When & Then
            assertThatThrownBy(() -> sessionService.getSession(TEST_SESSION_ID.toString(), otherUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("접근 권한이 없습니다");
        }

        @Test
        @DisplayName("삭제된 세션 조회 시 ConversationSessionNotFoundException")
        void getSession_삭제됨() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            session.setIsDeleted(true);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));

            // When & Then
            assertThatThrownBy(() -> sessionService.getSession(TEST_SESSION_ID.toString(), TEST_USER_ID))
                .isInstanceOf(ConversationSessionNotFoundException.class);
        }

        @Test
        @DisplayName("유효하지 않은 세션 ID 형식 시 InvalidSessionIdException")
        void getSession_잘못된_ID_형식() {
            // Given
            String invalidId = "invalid-id";

            // When & Then
            assertThatThrownBy(() -> sessionService.getSession(invalidId, TEST_USER_ID))
                .isInstanceOf(InvalidSessionIdException.class)
                .hasMessageContaining("유효하지 않은 세션 ID");
        }
    }

    // ========== updateLastMessageAt 테스트 ==========

    @Nested
    @DisplayName("updateLastMessageAt")
    class UpdateLastMessageAt {

        @Test
        @DisplayName("정상 업데이트")
        void updateLastMessageAt_성공() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));
            when(writerRepository.save(any(ConversationSessionEntity.class))).thenReturn(session);

            // When
            sessionService.updateLastMessageAt(TEST_SESSION_ID.toString());

            // Then
            verify(writerRepository).save(any(ConversationSessionEntity.class));
            verify(eventPublisher).publish(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("비활성 세션이 메시지 교환 시 자동 활성화")
        void updateLastMessageAt_자동_활성화() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            session.setIsActive(false);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));
            when(writerRepository.save(any(ConversationSessionEntity.class))).thenReturn(session);

            // When
            sessionService.updateLastMessageAt(TEST_SESSION_ID.toString());

            // Then
            verify(writerRepository).save(argThat(s -> s.getIsActive()));
        }

        @Test
        @DisplayName("존재하지 않는 세션은 ConversationSessionNotFoundException 발생")
        void updateLastMessageAt_미존재_예외() {
            // Given
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> sessionService.updateLastMessageAt(TEST_SESSION_ID.toString()))
                .isInstanceOf(ConversationSessionNotFoundException.class);
            verify(writerRepository, never()).save(any());
        }
    }

    // ========== listSessions 테스트 ==========

    @Nested
    @DisplayName("listSessions")
    class ListSessions {

        @Test
        @DisplayName("정상 조회 - Page<SessionResponse> 반환")
        void listSessions_성공() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            List<ConversationSessionEntity> sessions = List.of(
                createSessionEntity(1L, TEST_USER_ID),
                createSessionEntity(2L, TEST_USER_ID)
            );
            Page<ConversationSessionEntity> sessionPage = new PageImpl<>(sessions, pageable, 2);
            when(readerRepository.findByUserIdAndIsDeletedFalse(TEST_USER_ID, pageable))
                .thenReturn(sessionPage);

            // When
            Page<SessionResponse> result = sessionService.listSessions(TEST_USER_ID, pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("빈 결과 반환")
        void listSessions_빈_결과() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<ConversationSessionEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(readerRepository.findByUserIdAndIsDeletedFalse(TEST_USER_ID, pageable))
                .thenReturn(emptyPage);

            // When
            Page<SessionResponse> result = sessionService.listSessions(TEST_USER_ID, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ========== deleteSession 테스트 ==========

    @Nested
    @DisplayName("deleteSession")
    class DeleteSession {

        @Test
        @DisplayName("정상 삭제 (soft delete)")
        void deleteSession_성공() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));
            when(writerRepository.save(any(ConversationSessionEntity.class))).thenReturn(session);

            // When
            sessionService.deleteSession(TEST_SESSION_ID.toString(), TEST_USER_ID);

            // Then
            verify(writerRepository).save(argThat(s -> s.getIsDeleted()));
            verify(eventPublisher).publish(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("다른 사용자 세션 삭제 시 UnauthorizedException")
        void deleteSession_권한없음() {
            // Given
            String otherUserId = "2";
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));

            // When & Then
            assertThatThrownBy(() -> sessionService.deleteSession(TEST_SESSION_ID.toString(), otherUserId))
                .isInstanceOf(UnauthorizedException.class);
            verify(writerRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 삭제된 세션 삭제 시 ConversationSessionNotFoundException")
        void deleteSession_이미_삭제됨() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            session.setIsDeleted(true);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));

            // When & Then
            assertThatThrownBy(() -> sessionService.deleteSession(TEST_SESSION_ID.toString(), TEST_USER_ID))
                .isInstanceOf(ConversationSessionNotFoundException.class);
        }
    }

    // ========== updateSessionTitle 테스트 ==========

    @Nested
    @DisplayName("updateSessionTitle")
    class UpdateSessionTitle {

        @Test
        @DisplayName("정상 타이틀 업데이트 - SessionResponse 반환")
        void updateSessionTitle_성공() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));
            when(writerRepository.save(any(ConversationSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SessionResponse result = sessionService.updateSessionTitle(
                TEST_SESSION_ID.toString(), TEST_USER_ID, "새 타이틀");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("새 타이틀");
            verify(writerRepository).save(argThat(s -> "새 타이틀".equals(s.getTitle())));
            verify(eventPublisher).publish(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 세션 - ConversationSessionNotFoundException")
        void updateSessionTitle_미존재() {
            // Given
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> sessionService.updateSessionTitle(
                    TEST_SESSION_ID.toString(), TEST_USER_ID, "새 타이틀"))
                .isInstanceOf(ConversationSessionNotFoundException.class)
                .hasMessageContaining("세션을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("다른 사용자 세션 타이틀 변경 - UnauthorizedException")
        void updateSessionTitle_권한없음() {
            // Given
            String otherUserId = "2";
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));

            // When & Then
            assertThatThrownBy(() -> sessionService.updateSessionTitle(
                    TEST_SESSION_ID.toString(), otherUserId, "새 타이틀"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("접근 권한이 없습니다");
            verify(writerRepository, never()).save(any());
        }

        @Test
        @DisplayName("삭제된 세션 타이틀 변경 - ConversationSessionNotFoundException")
        void updateSessionTitle_삭제됨() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            session.setIsDeleted(true);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));

            // When & Then
            assertThatThrownBy(() -> sessionService.updateSessionTitle(
                    TEST_SESSION_ID.toString(), TEST_USER_ID, "새 타이틀"))
                .isInstanceOf(ConversationSessionNotFoundException.class);
        }

        @Test
        @DisplayName("Kafka 이벤트 페이로드에 title 포함 검증")
        void updateSessionTitle_kafkaEvent_검증() {
            // Given
            ConversationSessionEntity session = createSessionEntity(TEST_SESSION_ID, TEST_USER_ID);
            when(readerRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(session));
            when(writerRepository.save(any(ConversationSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            sessionService.updateSessionTitle(TEST_SESSION_ID.toString(), TEST_USER_ID, "새 타이틀");

            // Then
            verify(eventPublisher).publish(
                eq("tech-n-ai.conversation.session.updated"),
                any(),
                eq(TEST_SESSION_ID.toString()));
        }
    }

    // ========== deactivateInactiveSessions 테스트 ==========

    @Nested
    @DisplayName("deactivateInactiveSessions")
    class DeactivateInactiveSessions {

        @Test
        @DisplayName("비활성 세션 비활성화 - 개수 반환")
        void deactivateInactiveSessions_성공() {
            // Given
            Duration threshold = Duration.ofMinutes(30);
            List<ConversationSessionEntity> inactiveSessions = List.of(
                createSessionEntity(1L, TEST_USER_ID),
                createSessionEntity(2L, TEST_USER_ID)
            );
            when(readerRepository.findByIsActiveTrueAndIsDeletedFalseAndLastMessageAtBefore(any(LocalDateTime.class)))
                .thenReturn(inactiveSessions);
            when(writerRepository.save(any(ConversationSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            int result = sessionService.deactivateInactiveSessions(threshold);

            // Then
            assertThat(result).isEqualTo(2);
            verify(writerRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("비활성화할 세션 없으면 0 반환")
        void deactivateInactiveSessions_없음() {
            // Given
            Duration threshold = Duration.ofMinutes(30);
            when(readerRepository.findByIsActiveTrueAndIsDeletedFalseAndLastMessageAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

            // When
            int result = sessionService.deactivateInactiveSessions(threshold);

            // Then
            assertThat(result).isZero();
        }
    }

    // ========== 헬퍼 메서드 ==========

    private ConversationSessionEntity createSessionEntity(Long id, String userId) {
        ConversationSessionEntity entity = new ConversationSessionEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setTitle("테스트 세션");
        entity.setIsActive(true);
        entity.setIsDeleted(false);
        entity.setLastMessageAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
