package com.tech.n.ai.api.agent.agent;

import com.tech.n.ai.api.agent.config.AgentPromptConfig;
import com.tech.n.ai.api.agent.metrics.ToolExecutionMetrics;
import com.tech.n.ai.api.agent.tool.EmergingTechAgentTools;
import com.tech.n.ai.client.slack.domain.slack.contract.SlackContract;
import com.tech.n.ai.common.conversation.memory.MongoDbChatMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * EmergingTechAgentImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmergingTechAgentImpl 단위 테스트")
class EmergingTechAgentImplTest {

    @Mock
    private EmergingTechAgentTools tools;

    @Mock
    private AgentPromptConfig promptConfig;

    @Mock
    private SlackContract slackContract;

    @Mock
    private MongoDbChatMemoryStore mongoDbChatMemoryStore;

    @Mock
    private AgentAssistant assistant;

    private EmergingTechAgentImpl agentImpl;

    @BeforeEach
    void setUp() {
        // chatModel은 initAssistant에서만 사용되므로 null 전달 후 assistant를 직접 주입
        agentImpl = new EmergingTechAgentImpl(null, tools, promptConfig, slackContract, mongoDbChatMemoryStore);
        ReflectionTestUtils.setField(agentImpl, "assistant", assistant);
    }

    // ========== execute 테스트 ==========

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("정상 실행 - 성공 결과 반환")
        void execute_성공() {
            // Given
            String goal = "OpenAI 최신 업데이트 확인";
            String sessionId = "session-123";
            String prompt = "빌드된 프롬프트";
            when(promptConfig.buildPrompt(goal)).thenReturn(prompt);
            when(assistant.chat(sessionId, prompt)).thenReturn("3건의 업데이트 발견");

            // When
            AgentExecutionResult result = agentImpl.execute(goal, sessionId);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.summary()).isEqualTo("3건의 업데이트 발견");
            assertThat(result.sessionId()).isEqualTo(sessionId);
            verify(tools).bindMetrics(any(ToolExecutionMetrics.class));
            verify(tools).unbindMetrics();
        }

        @Test
        @DisplayName("실행 실패 - 예외 발생 시 failure 반환")
        void execute_실패() {
            // Given
            String goal = "목표";
            String sessionId = "session-456";
            when(promptConfig.buildPrompt(goal)).thenReturn("프롬프트");
            when(assistant.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 에러"));

            // When
            AgentExecutionResult result = agentImpl.execute(goal, sessionId);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.summary()).contains("Agent 실행 중 오류 발생");
            assertThat(result.sessionId()).isEqualTo(sessionId);
            assertThat(result.errors()).contains("LLM 에러");
        }

        @Test
        @DisplayName("실행 실패 시 Slack 에러 알림 전송")
        void execute_실패_슬랙알림() {
            // Given
            when(promptConfig.buildPrompt(anyString())).thenReturn("프롬프트");
            when(assistant.chat(anyString(), anyString())).thenThrow(new RuntimeException("에러"));

            // When
            agentImpl.execute("목표", "session");

            // Then
            verify(slackContract).sendErrorNotification(anyString(), any(Exception.class));
        }

        @Test
        @DisplayName("Slack 알림 실패해도 결과는 정상 반환")
        void execute_슬랙알림실패_무시() {
            // Given
            when(promptConfig.buildPrompt(anyString())).thenReturn("프롬프트");
            when(assistant.chat(anyString(), anyString())).thenThrow(new RuntimeException("에러"));
            doThrow(new RuntimeException("Slack 오류")).when(slackContract)
                .sendErrorNotification(anyString(), any(Exception.class));

            // When
            AgentExecutionResult result = agentImpl.execute("목표", "session");

            // Then
            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("finally 블록에서 항상 unbindMetrics 호출")
        void execute_unbindMetrics_항상호출() {
            // Given
            when(promptConfig.buildPrompt(anyString())).thenReturn("프롬프트");
            when(assistant.chat(anyString(), anyString())).thenThrow(new RuntimeException("에러"));

            // When
            agentImpl.execute("목표", "session");

            // Then
            verify(tools).unbindMetrics();
        }

        @Test
        @DisplayName("sessionId 없이 호출 시 자동 생성")
        void execute_sessionId자동생성() {
            // Given
            when(promptConfig.buildPrompt(anyString())).thenReturn("프롬프트");
            when(assistant.chat(anyString(), anyString())).thenReturn("완료");

            // When
            AgentExecutionResult result = agentImpl.execute("목표");

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.sessionId()).startsWith("agent-");
        }
    }

    // ========== evictSession 테스트 ==========

    @Nested
    @DisplayName("evictSession")
    class EvictSession {

        @Test
        @DisplayName("정상 제거 - true 반환")
        void evictSession_성공() {
            // Given
            when(assistant.evictChatMemory("session-123")).thenReturn(true);

            // When
            boolean result = agentImpl.evictSession("session-123");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 세션 - false 반환")
        void evictSession_미존재() {
            // Given
            when(assistant.evictChatMemory("unknown")).thenReturn(false);

            // When
            boolean result = agentImpl.evictSession("unknown");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("예외 발생 시 false 반환")
        void evictSession_예외() {
            // Given
            when(assistant.evictChatMemory(anyString())).thenThrow(new RuntimeException("메모리 에러"));

            // When
            boolean result = agentImpl.evictSession("session-123");

            // Then
            assertThat(result).isFalse();
        }
    }
}
