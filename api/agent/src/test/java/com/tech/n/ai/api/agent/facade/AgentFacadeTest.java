package com.tech.n.ai.api.agent.facade;

import com.tech.n.ai.api.agent.agent.AgentExecutionResult;
import com.tech.n.ai.api.agent.agent.EmergingTechAgent;
import com.tech.n.ai.api.agent.dto.request.AgentRunRequest;
import com.tech.n.ai.api.agent.service.SessionTitleGenerationService;
import com.tech.n.ai.api.agent.dto.response.AgentMessageListResponse;
import com.tech.n.ai.api.agent.dto.response.AgentSessionListResponse;
import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationMessageService;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentFacade 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentFacade 단위 테스트")
class AgentFacadeTest {

    @Mock
    private EmergingTechAgent agent;

    @Mock
    private ConversationSessionService conversationSessionService;

    @Mock
    private ConversationMessageService conversationMessageService;

    @Mock
    private SessionTitleGenerationService titleGenerationService;

    @InjectMocks
    private AgentFacade facade;

    private static final String TEST_USER_ID = "admin123";

    // ========== runAgent 테스트 ==========

    @Nested
    @DisplayName("runAgent")
    class RunAgent {

        @Test
        @DisplayName("정상 실행 - sessionId 제공된 경우")
        void runAgent_sessionId제공() {
            // Given
            String userId = "admin123";
            String goal = "OpenAI 최신 업데이트 확인";
            String sessionId = "custom-session-id";
            AgentRunRequest request = new AgentRunRequest(goal, sessionId);

            AgentExecutionResult expectedResult = AgentExecutionResult.success("완료", sessionId, 5, 2, 1000L);
            SessionResponse session = SessionResponse.builder().sessionId(sessionId).build();
            when(conversationSessionService.getSession(sessionId, userId)).thenReturn(session);
            when(agent.execute(goal, sessionId)).thenReturn(expectedResult);

            // When
            AgentExecutionResult result = facade.runAgent(userId, request);

            // Then
            assertThat(result).isEqualTo(expectedResult);
            verify(agent).execute(goal, sessionId);
            verify(conversationMessageService).saveMessage(eq(sessionId), eq("USER"), eq(goal), any());
            verify(conversationMessageService).saveMessage(eq(sessionId), eq("ASSISTANT"), eq("완료"), any());
            verify(conversationSessionService).updateLastMessageAt(sessionId);
            // 기존 세션에서는 타이틀 생성 미호출
            verify(titleGenerationService, never()).generateAndSaveTitleAsync(
                anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("sessionId 자동 생성 - 요청에 없는 경우")
        void runAgent_sessionId_null() {
            // Given
            String userId = "admin123";
            String goal = "목표";
            String newSessionId = "new-session-1";
            AgentRunRequest request = new AgentRunRequest(goal, null);

            when(conversationSessionService.createSession(userId, null)).thenReturn(newSessionId);
            AgentExecutionResult expectedResult = AgentExecutionResult.success("완료", newSessionId, 3, 1, 500L);
            when(agent.execute(eq(goal), eq(newSessionId))).thenReturn(expectedResult);

            // When
            AgentExecutionResult result = facade.runAgent(userId, request);

            // Then
            assertThat(result).isEqualTo(expectedResult);
            verify(conversationSessionService).createSession(userId, null);
            verify(titleGenerationService).generateAndSaveTitleAsync(
                eq(newSessionId), eq(userId), eq(goal), eq("완료"));
        }

        @Test
        @DisplayName("실패 결과 반환")
        void runAgent_실패결과() {
            // Given
            String userId = "admin";
            String sessionId = "session";
            AgentRunRequest request = new AgentRunRequest("goal", sessionId);

            SessionResponse session = SessionResponse.builder().sessionId(sessionId).build();
            when(conversationSessionService.getSession(sessionId, userId)).thenReturn(session);
            AgentExecutionResult failureResult = AgentExecutionResult.failure(
                    "실행 실패", sessionId, java.util.List.of("에러 메시지"));
            when(agent.execute(anyString(), anyString())).thenReturn(failureResult);

            // When
            AgentExecutionResult result = facade.runAgent(userId, request);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errors()).contains("에러 메시지");
            // 실패 시에도 ASSISTANT 메시지를 저장 (관리자가 실행 결과를 세션 재조회 시 확인 가능)
            verify(conversationMessageService).saveMessage(eq(sessionId), eq("USER"), eq("goal"), any());
            verify(conversationMessageService).saveMessage(eq(sessionId), eq("ASSISTANT"), eq("실행 실패"), any());
            // 실패 시에도 lastMessageAt은 업데이트
            verify(conversationSessionService).updateLastMessageAt(sessionId);
        }
    }

    // ========== listSessions 테스트 ==========

    @Nested
    @DisplayName("listSessions")
    class ListSessions {

        @Test
        @DisplayName("정상 조회 - AgentSessionListResponse 반환")
        void listSessions_성공() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            SessionResponse session = SessionResponse.builder()
                .sessionId("session-1")
                .title("Agent 대화")
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .isActive(true)
                .build();
            Page<SessionResponse> page = new PageImpl<>(List.of(session), pageable, 1);
            when(conversationSessionService.listSessions(TEST_USER_ID, pageable)).thenReturn(page);

            // When
            AgentSessionListResponse result = facade.listSessions(TEST_USER_ID, 1, 20, pageable);

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
            when(conversationSessionService.listSessions(TEST_USER_ID, pageable)).thenReturn(emptyPage);

            // When
            AgentSessionListResponse result = facade.listSessions(TEST_USER_ID, 1, 20, pageable);

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
        @DisplayName("정상 조회 - AgentMessageListResponse 반환")
        void listMessages_성공() {
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

            SessionResponse session = SessionResponse.builder().sessionId(sessionId).build();
            when(conversationSessionService.getSession(sessionId, TEST_USER_ID)).thenReturn(session);
            when(conversationMessageService.getMessages(sessionId, pageable)).thenReturn(page);

            // When
            AgentMessageListResponse result = facade.listMessages(sessionId, TEST_USER_ID, 1, 50, pageable);

            // Then
            assertThat(result.data().list()).hasSize(1);
            assertThat(result.data().list().get(0).messageId()).isEqualTo("msg-1");
            verify(conversationSessionService).getSession(sessionId, TEST_USER_ID);
        }

        @Test
        @DisplayName("세션 소유권 검증 호출 확인")
        void listMessages_소유권검증() {
            // Given
            String sessionId = "session-1";
            Pageable pageable = PageRequest.of(0, 50);
            Page<MessageResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            SessionResponse session = SessionResponse.builder().sessionId(sessionId).build();
            when(conversationSessionService.getSession(sessionId, TEST_USER_ID)).thenReturn(session);
            when(conversationMessageService.getMessages(sessionId, pageable)).thenReturn(emptyPage);

            // When
            facade.listMessages(sessionId, TEST_USER_ID, 1, 50, pageable);

            // Then
            verify(conversationSessionService).getSession(sessionId, TEST_USER_ID);
        }
    }
}
