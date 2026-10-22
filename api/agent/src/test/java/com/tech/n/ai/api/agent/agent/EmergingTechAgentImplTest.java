package com.tech.n.ai.api.agent.agent;

import com.tech.n.ai.api.agent.config.AgentPromptConfig;
import com.tech.n.ai.api.agent.metrics.ToolExecutionMetrics;
import com.tech.n.ai.api.agent.tool.EmergingTechAgentTools;
import com.tech.n.ai.api.agent.tool.adapter.AnalyticsToolAdapter;
import com.tech.n.ai.api.agent.tool.adapter.DataCollectionToolAdapter;
import com.tech.n.ai.api.agent.tool.adapter.EmergingTechToolAdapter;
import com.tech.n.ai.api.agent.tool.adapter.SlackToolAdapter;
import com.tech.n.ai.api.agent.tool.dto.StatisticsDto;
import com.tech.n.ai.client.slack.domain.slack.contract.SlackContract;
import com.tech.n.ai.common.conversation.memory.MongoDbChatMemoryStore;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

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

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // chatModel은 initAssistant에서만 사용되므로 null 전달 후 assistant를 직접 주입
        meterRegistry = new SimpleMeterRegistry();
        agentImpl = new EmergingTechAgentImpl(
            null, tools, promptConfig, slackContract, mongoDbChatMemoryStore, meterRegistry);
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
        @DisplayName("실행 한 번마다 Tool 호출 횟수를 미터에 기록")
        void execute_툴호출횟수_미터기록() {
            // Given
            when(promptConfig.buildPrompt(anyString())).thenReturn("프롬프트");
            when(assistant.chat(anyString(), anyString())).thenReturn("완료");

            // When
            agentImpl.execute("목표", "session");

            // Then
            assertThat(meterRegistry.summary("agent.tool.calls").count()).isEqualTo(1);
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

    // ========== 실행 중 대화 메모리 테스트 ==========

    @Nested
    @DisplayName("execute - 실행 중 대화 메모리")
    class ExecuteChatMemory {

        private static final String SESSION_ID = "session-memory";
        private static final String GOAL = "Provider별 수집 현황을 통계로 보여주세요";

        private final AgentPromptConfig realPromptConfig = new AgentPromptConfig();
        private ScriptedChatModel chatModel;
        private AnalyticsToolAdapter analyticsAdapter;
        private EmergingTechAgentImpl agent;

        @BeforeEach
        void setUpRealAssistant() {
            // 실제 @Tool 메서드가 리플렉션으로 잡히도록 Tool 객체는 진짜로 만들고 어댑터만 모의한다
            analyticsAdapter = mock(AnalyticsToolAdapter.class);
            EmergingTechAgentTools realTools = new EmergingTechAgentTools(
                    mock(SlackToolAdapter.class), mock(EmergingTechToolAdapter.class),
                    analyticsAdapter, mock(DataCollectionToolAdapter.class));
            when(analyticsAdapter.getStatistics(anyString(), anyString(), anyString()))
                    .thenReturn(new StatisticsDto("provider", "", "", 3,
                            List.of(new StatisticsDto.GroupCount("OPENAI", 3))));

            // 운영과 같은 조건: MongoDB에는 사용자가 보낸 goal 원문만 저장돼 있다
            when(mongoDbChatMemoryStore.getMessages((Object) SESSION_ID))
                    .thenReturn(List.of(UserMessage.from(GOAL)));

            chatModel = new ScriptedChatModel();
            agent = new EmergingTechAgentImpl(
                    chatModel, realTools, realPromptConfig, slackContract, mongoDbChatMemoryStore, meterRegistry);
            agent.initAssistant();
        }

        @Test
        @DisplayName("Tool 실행 뒤 두 번째 모델 요청에 규칙 프롬프트와 Tool 결과가 남고, 첫 요청 앞에는 저장된 대화 기록이 있다")
        void execute_두번째요청_프롬프트와Tool결과유지() {
            // When
            AgentExecutionResult result = agent.execute(GOAL, SESSION_ID);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(chatModel.requests).hasSize(2);

            List<ChatMessage> first = chatModel.requests.get(0).messages();
            assertThat(first.get(0)).isEqualTo(UserMessage.from(GOAL));

            List<ChatMessage> second = chatModel.requests.get(1).messages();
            assertThat(second).contains(UserMessage.from(realPromptConfig.buildPrompt(GOAL)));
            assertThat(second).anySatisfy(message -> assertThat(message).isInstanceOf(ToolExecutionResultMessage.class));
        }

        @Test
        @DisplayName("실행이 끝나면 세션 메모리를 비워, 같은 세션의 다음 실행은 저장된 대화 기록을 다시 읽는다")
        void execute_실행후메모리비움_다음실행시기록재조회() {
            // Given
            agent.execute(GOAL, SESSION_ID);

            // When
            agent.execute(GOAL, SESSION_ID);

            // Then
            verify(mongoDbChatMemoryStore, times(2)).getMessages((Object) SESSION_ID);
        }
    }

    /**
     * 실행마다 첫 응답은 통계 Tool 호출 요청, 둘째 응답은 텍스트를 돌려주고 받은 요청을 모두 기록하는 모델.
     */
    private static class ScriptedChatModel implements ChatModel {

        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            boolean toolCallTurn = requests.size() % 2 == 1;
            AiMessage answer = toolCallTurn
                    ? AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-" + requests.size())
                            .name("get_emerging_tech_statistics")
                            .arguments("{\"groupBy\":\"provider\",\"startDate\":\"\",\"endDate\":\"\"}")
                            .build())
                    : AiMessage.from("Provider별 통계입니다.");
            return ChatResponse.builder().aiMessage(answer).build();
        }
    }
}
