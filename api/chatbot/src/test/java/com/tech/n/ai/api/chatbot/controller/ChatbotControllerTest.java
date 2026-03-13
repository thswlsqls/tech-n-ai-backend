package com.tech.n.ai.api.chatbot.controller;

import com.tech.n.ai.api.chatbot.dto.request.ChatRequest;
import com.tech.n.ai.api.chatbot.dto.response.*;
import com.tech.n.ai.api.chatbot.facade.ChatbotFacade;
import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import com.tech.n.ai.common.core.dto.PageData;
import com.tech.n.ai.common.security.principal.UserPrincipal;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ChatbotController 단위 테스트
 *
 * standaloneSetup 사용하여 순수 Controller 로직만 테스트.
 * UserPrincipal은 커스텀 ArgumentResolver로 주입.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatbotController 단위 테스트")
class ChatbotControllerTest {

    @Mock
    private ChatbotFacade chatbotFacade;

    @Mock
    private ConversationSessionService sessionService;

    @InjectMocks
    private ChatbotController chatbotController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long TEST_USER_ID = 1L;
    private static final String BASE_URL = "/api/v1/chatbot";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // standaloneSetup으로 커스텀 ArgumentResolver 설정
        mockMvc = MockMvcBuilders
            .standaloneSetup(chatbotController)
            .setCustomArgumentResolvers(new TestUserPrincipalArgumentResolver())
            .build();
    }

    // ========== POST /api/v1/chatbot 테스트 ==========

    @Nested
    @DisplayName("POST /api/v1/chatbot")
    class Chat {

        @Test
        @DisplayName("정상 채팅 - 200 OK")
        void chat_성공() throws Exception {
            // Given
            ChatRequest request = new ChatRequest("안녕하세요", null);
            ChatResponse response = createChatResponse("안녕하세요! 무엇을 도와드릴까요?", "session-123");

            when(chatbotFacade.chat(any(ChatRequest.class), eq(TEST_USER_ID), eq("USER")))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.response").value("안녕하세요! 무엇을 도와드릴까요?"))
                .andExpect(jsonPath("$.data.conversationId").value("session-123"));
        }

        @Test
        @DisplayName("기존 세션으로 채팅 - 200 OK")
        void chat_기존_세션() throws Exception {
            // Given
            ChatRequest request = new ChatRequest("이전 대화 이어서", "session-123");
            ChatResponse response = createChatResponse("네, 이전 대화를 이어갑니다.", "session-123");

            when(chatbotFacade.chat(any(ChatRequest.class), eq(TEST_USER_ID), eq("USER")))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("session-123"));
        }

        @Test
        @DisplayName("메시지 누락 - 400 Bad Request")
        void chat_메시지_누락() throws Exception {
            // Given
            String body = """
                {"conversationId": "session-123"}
                """;

            // When & Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("빈 메시지 - 400 Bad Request")
        void chat_빈_메시지() throws Exception {
            // Given
            ChatRequest request = new ChatRequest("", null);

            // When & Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("500자 초과 메시지 - 400 Bad Request")
        void chat_메시지_길이_초과() throws Exception {
            // Given
            String longMessage = "a".repeat(501);
            ChatRequest request = new ChatRequest(longMessage, null);

            // When & Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    // ========== GET /api/v1/chatbot/sessions 테스트 ==========

    @Nested
    @DisplayName("GET /api/v1/chatbot/sessions")
    class GetSessions {

        @Test
        @DisplayName("세션 목록 조회 - 200 OK")
        void getSessions_성공() throws Exception {
            // Given
            List<SessionResponse> sessions = List.of(
                createSessionResponse("session-1", "대화 1"),
                createSessionResponse("session-2", "대화 2")
            );
            PageData<SessionResponse> pageData = PageData.of(20, 1, 2, sessions);
            SessionListResponse response = SessionListResponse.from(pageData);

            when(chatbotFacade.listSessions(eq(TEST_USER_ID), eq(1), eq(20), any(Pageable.class)))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions")
                    .param("page", "1")
                    .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.data.list").isArray())
                .andExpect(jsonPath("$.data.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.data.totalSize").value(2));
        }

        @Test
        @DisplayName("기본값 적용 조회 - 200 OK")
        void getSessions_기본값() throws Exception {
            // Given
            PageData<SessionResponse> pageData = PageData.of(20, 1, 0, List.of());
            SessionListResponse response = SessionListResponse.from(pageData);

            when(chatbotFacade.listSessions(eq(TEST_USER_ID), eq(1), eq(20), any(Pageable.class)))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions"))
                .andExpect(status().isOk());
        }
    }

    // ========== GET /api/v1/chatbot/sessions/{sessionId} 테스트 ==========

    @Nested
    @DisplayName("GET /api/v1/chatbot/sessions/{sessionId}")
    class GetSession {

        @Test
        @DisplayName("세션 상세 조회 - 200 OK")
        void getSession_성공() throws Exception {
            // Given
            SessionResponse session = createSessionResponse("session-123", "테스트 대화");
            when(sessionService.getSession("session-123", TEST_USER_ID.toString()))
                .thenReturn(session);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions/session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.sessionId").value("session-123"));
        }
    }

    // ========== GET /api/v1/chatbot/sessions/{sessionId}/messages 테스트 ==========

    @Nested
    @DisplayName("GET /api/v1/chatbot/sessions/{sessionId}/messages")
    class GetMessages {

        @Test
        @DisplayName("메시지 목록 조회 - 200 OK")
        void getMessages_성공() throws Exception {
            // Given
            List<MessageResponse> messages = List.of(
                createMessageResponse("msg-1", "session-123", "USER", "안녕하세요"),
                createMessageResponse("msg-2", "session-123", "ASSISTANT", "안녕하세요!")
            );
            PageData<MessageResponse> pageData = PageData.of(50, 1, 2, messages);
            MessageListResponse response = MessageListResponse.from(pageData);

            when(chatbotFacade.listMessages(eq("session-123"), eq(TEST_USER_ID), eq(1), eq(50), any(Pageable.class)))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions/session-123/messages")
                    .param("page", "1")
                    .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.data.pageSize").value(50))
                .andExpect(jsonPath("$.data.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.data.totalSize").value(2));
        }

        @Test
        @DisplayName("기본값 적용 조회 - 200 OK")
        void getMessages_기본값() throws Exception {
            // Given
            PageData<MessageResponse> pageData = PageData.of(50, 1, 0, List.of());
            MessageListResponse response = MessageListResponse.from(pageData);

            when(chatbotFacade.listMessages(eq("session-123"), eq(TEST_USER_ID), eq(1), eq(50), any(Pageable.class)))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(get(BASE_URL + "/sessions/session-123/messages"))
                .andExpect(status().isOk());
        }
    }

    // ========== PATCH /api/v1/chatbot/sessions/{sessionId}/title 테스트 ==========

    @Nested
    @DisplayName("PATCH /api/v1/chatbot/sessions/{sessionId}/title")
    class UpdateSessionTitle {

        @Test
        @DisplayName("세션 타이틀 수정 - 200 OK")
        void updateSessionTitle_성공() throws Exception {
            // Given
            SessionResponse session = createSessionResponse("session-123", "새 타이틀");
            when(sessionService.updateSessionTitle("session-123", TEST_USER_ID.toString(), "새 타이틀"))
                .thenReturn(session);

            String body = """
                {"title": "새 타이틀"}
                """;

            // When & Then
            mockMvc.perform(patch(BASE_URL + "/sessions/session-123/title")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.sessionId").value("session-123"))
                .andExpect(jsonPath("$.data.title").value("새 타이틀"));
        }

        @Test
        @DisplayName("빈 타이틀 - 400 Bad Request")
        void updateSessionTitle_빈_타이틀() throws Exception {
            // Given
            String body = """
                {"title": ""}
                """;

            // When & Then
            mockMvc.perform(patch(BASE_URL + "/sessions/session-123/title")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("타이틀 누락 - 400 Bad Request")
        void updateSessionTitle_타이틀_누락() throws Exception {
            // Given
            String body = "{}";

            // When & Then
            mockMvc.perform(patch(BASE_URL + "/sessions/session-123/title")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("200자 초과 타이틀 - 400 Bad Request")
        void updateSessionTitle_길이_초과() throws Exception {
            // Given
            String longTitle = "A".repeat(201);
            String body = """
                {"title": "%s"}
                """.formatted(longTitle);

            // When & Then
            mockMvc.perform(patch(BASE_URL + "/sessions/session-123/title")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }
    }

    // ========== DELETE /api/v1/chatbot/sessions/{sessionId} 테스트 ==========

    @Nested
    @DisplayName("DELETE /api/v1/chatbot/sessions/{sessionId}")
    class DeleteSession {

        @Test
        @DisplayName("세션 삭제 - 200 OK")
        void deleteSession_성공() throws Exception {
            // Given
            doNothing().when(sessionService).deleteSession("session-123", TEST_USER_ID.toString());

            // When & Then
            mockMvc.perform(delete(BASE_URL + "/sessions/session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"));

            verify(sessionService).deleteSession("session-123", TEST_USER_ID.toString());
        }
    }

    // ========== 헬퍼 클래스 ==========

    /**
     * 테스트용 UserPrincipal ArgumentResolver
     */
    static class TestUserPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(UserPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return new UserPrincipal(TEST_USER_ID, "test@example.com", "USER");
        }
    }

    // ========== 헬퍼 메서드 ==========

    private ChatResponse createChatResponse(String response, String conversationId) {
        return ChatResponse.builder()
            .response(response)
            .conversationId(conversationId)
            .sources(Collections.emptyList())
            .build();
    }

    private SessionResponse createSessionResponse(String sessionId, String title) {
        return SessionResponse.builder()
            .sessionId(sessionId)
            .title(title)
            .createdAt(LocalDateTime.now())
            .lastMessageAt(LocalDateTime.now())
            .isActive(true)
            .build();
    }

    private MessageResponse createMessageResponse(String messageId, String sessionId,
                                                   String role, String content) {
        return MessageResponse.builder()
            .messageId(messageId)
            .sessionId(sessionId)
            .role(role)
            .content(content)
            .tokenCount(10)
            .sequenceNumber(1)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
