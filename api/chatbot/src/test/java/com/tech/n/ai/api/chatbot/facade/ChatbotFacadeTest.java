package com.tech.n.ai.api.chatbot.facade;

import com.tech.n.ai.api.chatbot.dto.request.ChatRequest;
import com.tech.n.ai.api.chatbot.dto.response.ChatResponse;
import com.tech.n.ai.api.chatbot.dto.response.MessageListResponse;
import com.tech.n.ai.api.chatbot.dto.response.SessionListResponse;
import com.tech.n.ai.api.chatbot.service.ChatbotService;
import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationMessageService;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatbotFacade 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatbotFacade 단위 테스트")
class ChatbotFacadeTest {

    @Mock
    private ChatbotService chatbotService;

    @Mock
    private ConversationSessionService conversationSessionService;

    @Mock
    private ConversationMessageService conversationMessageService;

    @InjectMocks
    private ChatbotFacade facade;

    private static final Long TEST_USER_ID = 1L;

    // ========== chat 테스트 ==========

    @Nested
    @DisplayName("chat")
    class Chat {

        @Test
        @DisplayName("정상 호출 - ChatbotService에 위임")
        void chat_정상() {
            // Given
            ChatRequest request = new ChatRequest("안녕하세요", null);
            ChatResponse expected = ChatResponse.builder()
                .response("안녕하세요!")
                .conversationId("session-1")
                .sources(Collections.emptyList())
                .build();
            when(chatbotService.generateResponse(request, TEST_USER_ID, "USER")).thenReturn(expected);

            // When
            ChatResponse result = facade.chat(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result).isEqualTo(expected);
            verify(chatbotService).generateResponse(request, TEST_USER_ID, "USER");
        }
    }

    // ========== listSessions 테스트 ==========

    @Nested
    @DisplayName("listSessions")
    class ListSessions {

        @Test
        @DisplayName("정상 조회 - SessionListResponse 반환")
        void listSessions_정상() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            SessionResponse session = SessionResponse.builder()
                .sessionId("session-1")
                .title("대화 제목")
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .isActive(true)
                .build();
            Page<SessionResponse> page = new PageImpl<>(List.of(session), pageable, 1);
            when(conversationSessionService.listSessions(TEST_USER_ID.toString(), pageable)).thenReturn(page);

            // When
            SessionListResponse result = facade.listSessions(TEST_USER_ID, 1, 20, pageable);

            // Then
            assertThat(result.data().list()).hasSize(1);
            assertThat(result.data().list().get(0).sessionId()).isEqualTo("session-1");
            assertThat(result.data().totalSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 결과 - 빈 리스트 반환")
        void listSessions_빈결과() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            Page<SessionResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(conversationSessionService.listSessions(TEST_USER_ID.toString(), pageable)).thenReturn(emptyPage);

            // When
            SessionListResponse result = facade.listSessions(TEST_USER_ID, 1, 20, pageable);

            // Then
            assertThat(result.data().list()).isEmpty();
            assertThat(result.data().totalSize()).isEqualTo(0);
        }
    }

    // ========== listMessages 테스트 ==========

    @Nested
    @DisplayName("listMessages")
    class ListMessages {

        @Test
        @DisplayName("정상 조회 - MessageListResponse 반환")
        void listMessages_정상() {
            // Given
            String sessionId = "session-1";
            Pageable pageable = PageRequest.of(0, 50);
            MessageResponse msg = MessageResponse.builder()
                .messageId("msg-1")
                .sessionId(sessionId)
                .role("USER")
                .content("테스트 메시지")
                .sequenceNumber(1)
                .createdAt(LocalDateTime.now())
                .build();
            Page<MessageResponse> page = new PageImpl<>(List.of(msg), pageable, 1);

            when(conversationMessageService.getMessages(sessionId, pageable)).thenReturn(page);

            // When
            MessageListResponse result = facade.listMessages(sessionId, TEST_USER_ID, 1, 50, pageable);

            // Then
            assertThat(result.data().list()).hasSize(1);
            assertThat(result.data().list().get(0).messageId()).isEqualTo("msg-1");
            verify(conversationSessionService).getSession(sessionId, TEST_USER_ID.toString());
        }

        @Test
        @DisplayName("세션 소유권 검증 호출 확인")
        void listMessages_소유권검증() {
            // Given
            String sessionId = "session-1";
            Pageable pageable = PageRequest.of(0, 50);
            Page<MessageResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(conversationMessageService.getMessages(sessionId, pageable)).thenReturn(emptyPage);

            // When
            facade.listMessages(sessionId, TEST_USER_ID, 1, 50, pageable);

            // Then
            verify(conversationSessionService).getSession(sessionId, TEST_USER_ID.toString());
        }

        @Test
        @DisplayName("소유권 검증 실패 시 메시지 조회 차단")
        void listMessages_소유권검증실패() {
            // Given
            String sessionId = "session-1";
            Pageable pageable = PageRequest.of(0, 50);
            when(conversationSessionService.getSession(sessionId, TEST_USER_ID.toString()))
                .thenThrow(new com.tech.n.ai.common.exception.exception.UnauthorizedException("접근 권한 없음"));

            // When & Then
            org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> facade.listMessages(sessionId, TEST_USER_ID, 1, 50, pageable))
                .isInstanceOf(com.tech.n.ai.common.exception.exception.UnauthorizedException.class);
            verify(conversationMessageService, never()).getMessages(any(), any());
        }
    }
}
