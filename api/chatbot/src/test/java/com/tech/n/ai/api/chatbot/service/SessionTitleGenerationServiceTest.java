package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SessionTitleGenerationService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionTitleGenerationService 단위 테스트")
class SessionTitleGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ConversationSessionService sessionService;

    @InjectMocks
    private SessionTitleGenerationServiceImpl titleGenerationService;

    private static final String TEST_SESSION_ID = "100";
    private static final Long TEST_USER_ID = 1L;

    // ========== 정상 타이틀 생성 테스트 ==========

    @Nested
    @DisplayName("generateAndSaveTitleAsync - 정상 동작")
    class NormalOperation {

        @Test
        @DisplayName("정상 타이틀 생성 및 저장")
        void generateAndSaveTitle_성공() {
            // Given
            when(chatModel.chat(anyString())).thenReturn("AI 트렌드 대화");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, "AI 트렌드 대화"));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "최신 AI 기술 트렌드 알려줘", "최신 AI 기술 트렌드를 알려드리겠습니다.");

            // Then
            verify(chatModel).chat(anyString());
            verify(sessionService).updateSessionTitle(TEST_SESSION_ID, TEST_USER_ID.toString(), "AI 트렌드 대화");
        }

        @Test
        @DisplayName("ChatModel에 올바른 프롬프트 전달 검증")
        void generateAndSaveTitle_프롬프트_검증() {
            // Given
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            when(chatModel.chat(anyString())).thenReturn("AI 트렌드 분석");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, "AI 트렌드 분석"));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "AI 기술 트렌드", "AI 기술 트렌드 답변");

            // Then
            verify(chatModel).chat(promptCaptor.capture());
            String prompt = promptCaptor.getValue();
            assertThat(prompt).contains("AI 기술 트렌드");
            assertThat(prompt).contains("AI 기술 트렌드 답변");
            assertThat(prompt).contains("Generate a concise title");
            assertThat(prompt).contains("same language as the user's message");
        }
    }

    // ========== sanitizeTitle 테스트 ==========

    @Nested
    @DisplayName("generateAndSaveTitleAsync - 타이틀 새니타이즈")
    class SanitizeTitle {

        @Test
        @DisplayName("LLM 응답에 큰따옴표 포함 시 제거")
        void sanitizeTitle_큰따옴표_제거() {
            // Given
            when(chatModel.chat(anyString())).thenReturn("\"AI 트렌드 대화\"");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, "AI 트렌드 대화"));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답");

            // Then
            verify(sessionService).updateSessionTitle(TEST_SESSION_ID, TEST_USER_ID.toString(), "AI 트렌드 대화");
        }

        @Test
        @DisplayName("LLM 응답에 작은따옴표 포함 시 제거")
        void sanitizeTitle_작은따옴표_제거() {
            // Given
            when(chatModel.chat(anyString())).thenReturn("'AI 기술 동향'");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, "AI 기술 동향"));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답");

            // Then
            verify(sessionService).updateSessionTitle(TEST_SESSION_ID, TEST_USER_ID.toString(), "AI 기술 동향");
        }

        @Test
        @DisplayName("200자 초과 타이틀은 200자로 잘라내기")
        void sanitizeTitle_200자_초과() {
            // Given
            String longTitle = "A".repeat(250);
            when(chatModel.chat(anyString())).thenReturn(longTitle);
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, longTitle.substring(0, 200)));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답");

            // Then
            ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
            verify(sessionService).updateSessionTitle(eq(TEST_SESSION_ID), eq(TEST_USER_ID.toString()), titleCaptor.capture());
            assertThat(titleCaptor.getValue()).hasSize(200);
        }

        @Test
        @DisplayName("LLM 빈 응답 시 updateSessionTitle 호출 안 함")
        void sanitizeTitle_빈_응답() {
            // Given
            when(chatModel.chat(anyString())).thenReturn("");

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답");

            // Then
            verify(sessionService, never()).updateSessionTitle(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("LLM null 응답 시 updateSessionTitle 호출 안 함")
        void sanitizeTitle_null_응답() {
            // Given
            when(chatModel.chat(anyString())).thenReturn(null);

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답");

            // Then
            verify(sessionService, never()).updateSessionTitle(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("공백만 있는 응답 시 updateSessionTitle 호출 안 함")
        void sanitizeTitle_공백_응답() {
            // Given
            when(chatModel.chat(anyString())).thenReturn("   ");

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답");

            // Then
            verify(sessionService, never()).updateSessionTitle(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("따옴표만 있는 응답 시 updateSessionTitle 호출 안 함")
        void sanitizeTitle_따옴표만() {
            // Given
            when(chatModel.chat(anyString())).thenReturn("\"\"");

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답");

            // Then
            verify(sessionService, never()).updateSessionTitle(anyString(), anyString(), anyString());
        }
    }

    // ========== 입력 truncation 테스트 ==========

    @Nested
    @DisplayName("generateAndSaveTitleAsync - 입력 truncation")
    class InputTruncation {

        @Test
        @DisplayName("300자 초과 사용자 메시지가 잘려서 프롬프트에 포함")
        void truncate_사용자_메시지_300자_초과() {
            // Given
            String longMessage = "A".repeat(400);
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            when(chatModel.chat(anyString())).thenReturn("테스트 타이틀");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, "테스트 타이틀"));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, longMessage, "짧은 응답");

            // Then
            verify(chatModel).chat(promptCaptor.capture());
            String prompt = promptCaptor.getValue();
            // 300자 + "..." = 303자까지만 포함, 원본 400자는 포함되지 않음
            assertThat(prompt).doesNotContain("A".repeat(400));
            assertThat(prompt).contains("A".repeat(300) + "...");
        }

        @Test
        @DisplayName("300자 초과 AI 응답이 잘려서 프롬프트에 포함")
        void truncate_AI_응답_300자_초과() {
            // Given
            String longResponse = "B".repeat(400);
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            when(chatModel.chat(anyString())).thenReturn("테스트 타이틀");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, "테스트 타이틀"));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, "짧은 메시지", longResponse);

            // Then
            verify(chatModel).chat(promptCaptor.capture());
            String prompt = promptCaptor.getValue();
            assertThat(prompt).doesNotContain("B".repeat(400));
            assertThat(prompt).contains("B".repeat(300) + "...");
        }

        @Test
        @DisplayName("null 입력은 빈 문자열로 처리")
        void truncate_null_입력() {
            // Given
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            when(chatModel.chat(anyString())).thenReturn("기본 타이틀");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenReturn(createSessionResponse(TEST_SESSION_ID, "기본 타이틀"));

            // When
            titleGenerationService.generateAndSaveTitleAsync(
                TEST_SESSION_ID, TEST_USER_ID, null, "응답");

            // Then
            verify(chatModel).chat(promptCaptor.capture());
            // null이 빈 문자열로 처리되어 프롬프트가 정상 생성됨
            assertThat(promptCaptor.getValue()).contains("Generate a concise title");
        }
    }

    // ========== 예외 처리 테스트 ==========

    @Nested
    @DisplayName("generateAndSaveTitleAsync - 예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("ChatModel 예외 발생 시 예외 흡수 (전파하지 않음)")
        void chatModel_예외_흡수() {
            // Given
            when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM API 오류"));

            // When & Then
            assertThatNoException().isThrownBy(() ->
                titleGenerationService.generateAndSaveTitleAsync(
                    TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답"));

            verify(sessionService, never()).updateSessionTitle(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("sessionService.updateSessionTitle 예외 발생 시 예외 흡수")
        void updateSessionTitle_예외_흡수() {
            // Given
            when(chatModel.chat(anyString())).thenReturn("타이틀");
            when(sessionService.updateSessionTitle(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB 오류"));

            // When & Then
            assertThatNoException().isThrownBy(() ->
                titleGenerationService.generateAndSaveTitleAsync(
                    TEST_SESSION_ID, TEST_USER_ID, "메시지", "응답"));
        }
    }

    // ========== 헬퍼 메서드 ==========

    private SessionResponse createSessionResponse(String sessionId, String title) {
        return SessionResponse.builder()
            .sessionId(sessionId)
            .title(title)
            .createdAt(LocalDateTime.now())
            .lastMessageAt(LocalDateTime.now())
            .isActive(true)
            .build();
    }
}
