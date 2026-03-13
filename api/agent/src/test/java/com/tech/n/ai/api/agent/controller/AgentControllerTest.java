package com.tech.n.ai.api.agent.controller;

import com.tech.n.ai.api.agent.agent.AgentExecutionResult;
import com.tech.n.ai.api.agent.dto.request.AgentRunRequest;
import com.tech.n.ai.api.agent.dto.response.AgentMessageListResponse;
import com.tech.n.ai.api.agent.dto.response.AgentSessionListResponse;
import com.tech.n.ai.api.agent.facade.AgentFacade;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgentController 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentController 단위 테스트")
class AgentControllerTest {

    @Mock
    private AgentFacade agentFacade;

    @Mock
    private ConversationSessionService conversationSessionService;

    @InjectMocks
    private AgentController agentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/v1/agent";
    private static final String TEST_USER_ID = "admin123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders
                .standaloneSetup(agentController)
                .build();
    }

    // ========== POST /api/v1/agent/run 테스트 ==========

    @Nested
    @DisplayName("POST /api/v1/agent/run")
    class RunAgent {

        @Test
        @DisplayName("정상 실행 - 200 OK")
        void runAgent_정상실행() throws Exception {
            // Given
            AgentRunRequest request = new AgentRunRequest("OpenAI 최신 업데이트 확인", "session-123");
            AgentExecutionResult result = AgentExecutionResult.success(
                    "실행 완료: 3건의 업데이트 발견", "session-123", 5, 2, 1500L);

            when(agentFacade.runAgent(eq(TEST_USER_ID), any(AgentRunRequest.class)))
                    .thenReturn(result);

            // When & Then
            mockMvc.perform(post(BASE_URL + "/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-user-id", TEST_USER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("2000"))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.summary").value("실행 완료: 3건의 업데이트 발견"))
                    .andExpect(jsonPath("$.data.sessionId").value("session-123"))
                    .andExpect(jsonPath("$.data.toolCallCount").value(5))
                    .andExpect(jsonPath("$.data.analyticsCallCount").value(2));
        }

        @Test
        @DisplayName("sessionId 선택적 - 없어도 성공")
        void runAgent_sessionId없음() throws Exception {
            // Given
            String requestBody = """
                    {"goal": "목표만 있는 요청"}
                    """;
            AgentExecutionResult result = AgentExecutionResult.success("완료", "auto-session", 1, 0, 100L);

            when(agentFacade.runAgent(eq(TEST_USER_ID), any(AgentRunRequest.class)))
                    .thenReturn(result);

            // When & Then
            mockMvc.perform(post(BASE_URL + "/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-user-id", TEST_USER_ID)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("2000"));
        }

        @Test
        @DisplayName("x-user-id 헤더 전달 검증")
        void runAgent_userId헤더전달() throws Exception {
            // Given
            String customUserId = "custom-user-456";
            AgentRunRequest request = new AgentRunRequest("목표", null);
            AgentExecutionResult result = AgentExecutionResult.success("완료", "session-1", 0, 0, 0);

            when(agentFacade.runAgent(eq(customUserId), any(AgentRunRequest.class)))
                    .thenReturn(result);

            // When & Then
            mockMvc.perform(post(BASE_URL + "/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-user-id", customUserId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(agentFacade).runAgent(eq(customUserId), any(AgentRunRequest.class));
        }

        @Test
        @DisplayName("실행 실패 시 에러 정보 포함")
        void runAgent_실패응답() throws Exception {
            // Given
            AgentRunRequest request = new AgentRunRequest("목표", null);
            AgentExecutionResult failureResult = AgentExecutionResult.failure(
                    "실행 중 오류 발생", null, List.of("에러1", "에러2"));

            when(agentFacade.runAgent(any(), any())).thenReturn(failureResult);

            // When & Then
            mockMvc.perform(post(BASE_URL + "/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-user-id", TEST_USER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(false))
                    .andExpect(jsonPath("$.data.errors").isArray())
                    .andExpect(jsonPath("$.data.errors[0]").value("에러1"))
                    .andExpect(jsonPath("$.data.errors[1]").value("에러2"));
        }

        @Test
        @DisplayName("x-user-id 헤더 누락 시 400 Bad Request")
        void runAgent_헤더누락() throws Exception {
            // Given
            AgentRunRequest request = new AgentRunRequest("목표", null);

            // When & Then
            mockMvc.perform(post(BASE_URL + "/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========== GET /api/v1/agent/sessions/{sessionId} 테스트 ==========

    @Nested
    @DisplayName("GET /api/v1/agent/sessions/{sessionId}")
    class GetSession {

        @Test
        @DisplayName("세션 상세 조회 - 200 OK")
        void getSession_성공() throws Exception {
            // Given
            SessionResponse session = SessionResponse.builder()
                .sessionId("session-123")
                .title("Agent 대화")
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .isActive(true)
                .build();
            when(conversationSessionService.getSession("session-123", TEST_USER_ID))
                .thenReturn(session);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions/session-123")
                    .header("x-user-id", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.sessionId").value("session-123"));
        }
    }

    // ========== GET /api/v1/agent/sessions 테스트 ==========

    @Nested
    @DisplayName("GET /api/v1/agent/sessions")
    class GetSessions {

        @Test
        @DisplayName("세션 목록 조회 - 200 OK")
        void getSessions_성공() throws Exception {
            // Given
            SessionResponse session = SessionResponse.builder()
                .sessionId("session-1")
                .title("Agent 대화")
                .createdAt(LocalDateTime.of(2026, 3, 13, 10, 0))
                .lastMessageAt(LocalDateTime.of(2026, 3, 13, 10, 5))
                .isActive(true)
                .build();
            AgentSessionListResponse response = AgentSessionListResponse.from(
                com.tech.n.ai.common.core.dto.PageData.of(20, 1, 1, List.of(session)));

            when(agentFacade.listSessions(eq(TEST_USER_ID), eq(1), eq(20), any()))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions")
                    .header("x-user-id", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.data.list[0].sessionId").value("session-1"));
        }

        @Test
        @DisplayName("페이지 파라미터 지정")
        void getSessions_페이지파라미터() throws Exception {
            // Given
            AgentSessionListResponse response = AgentSessionListResponse.from(
                com.tech.n.ai.common.core.dto.PageData.of(10, 2, 0, List.of()));

            when(agentFacade.listSessions(eq(TEST_USER_ID), eq(2), eq(10), any()))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions")
                    .param("page", "2")
                    .param("size", "10")
                    .header("x-user-id", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.data.pageSize").value(10));
        }
    }

    // ========== GET /api/v1/agent/sessions/{sessionId}/messages 테스트 ==========

    @Nested
    @DisplayName("GET /api/v1/agent/sessions/{sessionId}/messages")
    class GetMessages {

        @Test
        @DisplayName("메시지 목록 조회 - 200 OK")
        void getMessages_성공() throws Exception {
            // Given
            com.tech.n.ai.common.conversation.dto.MessageResponse msg =
                com.tech.n.ai.common.conversation.dto.MessageResponse.builder()
                    .messageId("msg-1")
                    .sessionId("session-123")
                    .role("USER")
                    .content("테스트 메시지")
                    .sequenceNumber(1)
                    .createdAt(LocalDateTime.of(2026, 3, 13, 10, 0))
                    .build();
            AgentMessageListResponse response = AgentMessageListResponse.from(
                com.tech.n.ai.common.core.dto.PageData.of(50, 1, 1, List.of(msg)));

            when(agentFacade.listMessages(eq("session-123"), eq(TEST_USER_ID), eq(1), eq(50), any()))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions/session-123/messages")
                    .header("x-user-id", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.data.list[0].messageId").value("msg-1"))
                .andExpect(jsonPath("$.data.data.list[0].role").value("USER"));
        }

        @Test
        @DisplayName("페이지 파라미터 지정")
        void getMessages_페이지파라미터() throws Exception {
            // Given
            AgentMessageListResponse response = AgentMessageListResponse.from(
                com.tech.n.ai.common.core.dto.PageData.of(30, 2, 0, List.of()));

            when(agentFacade.listMessages(eq("session-123"), eq(TEST_USER_ID), eq(2), eq(30), any()))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions/session-123/messages")
                    .param("page", "2")
                    .param("size", "30")
                    .header("x-user-id", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.data.pageSize").value(30));
        }
    }

    // ========== DELETE /api/v1/agent/sessions/{sessionId} 테스트 ==========

    @Nested
    @DisplayName("DELETE /api/v1/agent/sessions/{sessionId}")
    class DeleteSession {

        @Test
        @DisplayName("세션 삭제 - 200 OK")
        void deleteSession_성공() throws Exception {
            // Given
            doNothing().when(conversationSessionService).deleteSession("session-123", TEST_USER_ID);

            // When & Then
            mockMvc.perform(delete(BASE_URL + "/sessions/session-123")
                    .header("x-user-id", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"));

            verify(conversationSessionService).deleteSession("session-123", TEST_USER_ID);
        }
    }
}
