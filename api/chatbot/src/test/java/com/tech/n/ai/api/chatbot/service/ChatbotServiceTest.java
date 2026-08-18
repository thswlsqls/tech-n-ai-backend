package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.chain.AnswerGenerationChain;
import com.tech.n.ai.api.chatbot.chain.InputInterpretationChain;
import com.tech.n.ai.api.chatbot.chain.ResultRefinementChain;
import com.tech.n.ai.api.chatbot.dto.request.ChatRequest;
import com.tech.n.ai.api.chatbot.dto.response.ChatResponse;
import com.tech.n.ai.api.chatbot.memory.ConversationChatMemoryProvider;
import com.tech.n.ai.common.conversation.service.ConversationMessageService;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.Intent;
import com.tech.n.ai.api.chatbot.service.dto.SearchContext;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchQuery;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.api.chatbot.service.dto.WebSearchDocument;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatbotService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatbotService 단위 테스트")
class ChatbotServiceTest {

    @Mock
    private ConversationSessionService sessionService;

    @Mock
    private ConversationMessageService messageService;

    @Mock
    private ConversationChatMemoryProvider memoryProvider;

    @Mock
    private LLMService llmService;

    @Mock
    private TokenService tokenService;

    @Mock
    private IntentClassificationService intentService;

    @Mock
    private InputInterpretationChain inputChain;

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private GraphSearchService graphSearchService;

    @Mock
    private ResultRefinementChain refinementChain;

    @Mock
    private AnswerGenerationChain answerChain;

    @Mock
    private WebSearchService webSearchService;

    @Mock
    private PromptService promptService;

    @Mock
    private AgentDelegationService agentDelegationService;

    @Mock
    private SessionTitleGenerationService titleGenerationService;

    @Mock
    private ChatMemory chatMemory;

    // 목이면 summary(...)가 null을 돌려줘 NPE가 난다. 실제 레지스트리를 쓴다.
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private ChatbotServiceImpl chatbotService;

    @Captor
    private ArgumentCaptor<List<ChatMessage>> messagesCaptor;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_SESSION_ID = "100";

    @BeforeEach
    void setUp() {
        // SearchOptionsFactory는 실물을 주입한다. 목으로 두면 create()가 null을 돌려준다.
        SearchOptionsFactory searchOptionsFactory = new SearchOptionsFactory();
        ReflectionTestUtils.setField(searchOptionsFactory, "maxSearchResults", 5);
        ReflectionTestUtils.setField(searchOptionsFactory, "minSimilarityScore", 0.7);
        ReflectionTestUtils.setField(searchOptionsFactory, "recencyMonths", 6);
        ReflectionTestUtils.setField(chatbotService, "searchOptionsFactory", searchOptionsFactory);

        // RetrievalService도 실물을 주입한다. 벡터 검색 목은 위에서 만든 것을 그대로 물려
        // 이 테스트의 스텁·검증이 검색 호출을 그대로 잡도록 한다.
        // 그래프 검색 목은 운영 기본값대로 꺼진 결과를 돌려준다.
        RetrievalService retrievalService =
            new RetrievalService(vectorSearchService, graphSearchService);
        lenient().when(graphSearchService.search(anyString())).thenReturn(GraphSearchOutcome.disabled());
        ReflectionTestUtils.setField(chatbotService, "retrievalService", retrievalService);
    }

    // ========== LLM_DIRECT Intent 테스트 ==========

    @Nested
    @DisplayName("generateResponse - LLM_DIRECT")
    class LlmDirectIntent {

        @Test
        @DisplayName("일반 대화 처리 - 새 세션 생성")
        void generateResponse_llmDirect_newSession() {
            // Given
            ChatRequest request = new ChatRequest("안녕하세요", null);
            setupCommonMocks(Intent.LLM_DIRECT);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);
            when(llmService.generate(anyList())).thenReturn("안녕하세요! 무엇을 도와드릴까요?");

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result.response()).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
            assertThat(result.conversationId()).isEqualTo(TEST_SESSION_ID);
            assertThat(result.sources()).isEmpty();
            verify(sessionService).createSession(TEST_USER_ID.toString(), null);
        }

        @Test
        @DisplayName("일반 대화 처리 - 기존 세션 사용")
        void generateResponse_llmDirect_existingSession() {
            // Given
            ChatRequest request = new ChatRequest("이전 대화 이어서", TEST_SESSION_ID);
            setupCommonMocks(Intent.LLM_DIRECT);
            when(messageService.getMessagesForMemory(eq(TEST_SESSION_ID), any())).thenReturn(Collections.emptyList());
            when(llmService.generate(anyList())).thenReturn("네, 이어서 진행합니다.");

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result.conversationId()).isEqualTo(TEST_SESSION_ID);
            verify(sessionService).getSession(TEST_SESSION_ID, TEST_USER_ID.toString());
            verify(sessionService, never()).createSession(anyString(), any());
        }

        @Test
        @DisplayName("일반 대화 처리 - 대화 메시지 목록을 그대로 LLM에 넘긴다")
        void generateResponse_llmDirect_메시지목록_전달() {
            // Given: 이전 대화 두 건이 저장돼 있는 기존 세션
            ChatRequest request = new ChatRequest("RAG가 뭐야?", TEST_SESSION_ID);
            when(memoryProvider.get(anyString())).thenReturn(MessageWindowChatMemory.withMaxMessages(10));
            when(intentService.classifyIntent(anyString())).thenReturn(Intent.LLM_DIRECT);
            when(messageService.getMessagesForMemory(eq(TEST_SESSION_ID), any())).thenReturn(List.of(
                UserMessage.from("안녕하세요"),
                AiMessage.from("안녕하세요! 무엇을 도와드릴까요?")));
            when(llmService.generate(anyList())).thenReturn("검색한 문서를 근거로 답하는 방식입니다.");

            // When
            chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then: 이전 대화 두 건 뒤에 이번 질문이 붙은 목록이 그대로 넘어간다
            verify(llmService).generate(messagesCaptor.capture());
            List<ChatMessage> sentMessages = messagesCaptor.getValue();
            assertThat(sentMessages).hasSize(3);
            assertThat(sentMessages.get(0)).isInstanceOf(UserMessage.class);
            assertThat(((UserMessage) sentMessages.get(0)).singleText()).isEqualTo("안녕하세요");
            assertThat(sentMessages.get(1)).isInstanceOf(AiMessage.class);
            assertThat(((AiMessage) sentMessages.get(1)).text()).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
            assertThat(sentMessages.get(2)).isInstanceOf(UserMessage.class);
            assertThat(((UserMessage) sentMessages.get(2)).singleText()).isEqualTo("RAG가 뭐야?");
        }
    }

    // ========== RAG_REQUIRED Intent 테스트 ==========

    @Nested
    @DisplayName("generateResponse - RAG_REQUIRED")
    class RagRequiredIntent {

        @Test
        @DisplayName("RAG 파이프라인 처리")
        void generateResponse_ragRequired() {
            // Given
            ChatRequest request = new ChatRequest("대회 정보 알려줘", null);
            setupCommonMocks(Intent.RAG_REQUIRED);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);

            SearchContext context = new SearchContext();
            context.addCollection("emerging_techs");
            SearchQuery searchQuery = SearchQuery.builder()
                .query("대회 정보")
                .context(context)
                .build();
            when(inputChain.interpret(anyString())).thenReturn(searchQuery);

            List<SearchResult> searchResults = List.of(
                SearchResult.builder()
                    .documentId("doc1")
                    .text("대회 정보 내용")
                    .score(0.9)
                    .collectionType("EMERGING_TECH")
                    .build()
            );
            when(vectorSearchService.search(anyString(), anyLong(), any())).thenReturn(
                SearchOutcome.builder()
                    .path(SearchPath.HYBRID)
                    .candidates(searchResults)
                    .results(searchResults)
                    .build());
            when(refinementChain.refine(anyString(), anyList(), anyBoolean(), anyBoolean())).thenReturn(searchResults);
            when(answerChain.generate(anyString(), anyList())).thenReturn("대회 정보에 대한 답변입니다.");

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result.response()).isEqualTo("대회 정보에 대한 답변입니다.");
            assertThat(result.sources()).hasSize(1);
            verify(inputChain).interpret(anyString());
            verify(vectorSearchService).search(anyString(), anyLong(), any());
            verify(refinementChain).refine(anyString(), anyList(), anyBoolean(), anyBoolean());
            verify(answerChain).generate(anyString(), anyList());
            // 검색이 몇 건을 물고 왔는지 미터에 남는다
            assertThat(meterRegistry.summary("chatbot.search.results").count()).isEqualTo(1);
            assertThat(meterRegistry.summary("chatbot.search.results").totalAmount()).isEqualTo(1.0);
        }
    }

    // ========== WEB_SEARCH_REQUIRED Intent 테스트 ==========

    @Nested
    @DisplayName("generateResponse - WEB_SEARCH_REQUIRED")
    class WebSearchRequiredIntent {

        @Test
        @DisplayName("웹 검색 파이프라인 처리")
        void generateResponse_webSearchRequired() {
            // Given
            ChatRequest request = new ChatRequest("오늘 AI 뉴스", null);
            setupCommonMocks(Intent.WEB_SEARCH_REQUIRED);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);

            List<WebSearchDocument> webResults = List.of(
                new WebSearchDocument("AI 뉴스 제목", "https://example.com", "뉴스 요약", "example.com")
            );
            when(webSearchService.search(anyString())).thenReturn(webResults);
            when(promptService.buildWebSearchPrompt(anyString(), anyList())).thenReturn("웹 검색 프롬프트");
            when(llmService.generate(anyString())).thenReturn("웹 검색 결과 기반 답변");

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result.response()).isEqualTo("웹 검색 결과 기반 답변");
            assertThat(result.sources()).hasSize(1);
            assertThat(result.sources().get(0).url()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("웹 검색 결과 없을 때 LLM fallback")
        void generateResponse_webSearchEmpty_fallback() {
            // Given
            ChatRequest request = new ChatRequest("오늘 날씨", null);
            setupCommonMocks(Intent.WEB_SEARCH_REQUIRED);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);
            when(webSearchService.search(anyString())).thenReturn(Collections.emptyList());
            when(llmService.generate(anyString())).thenReturn("날씨 정보를 찾을 수 없습니다.");

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result.response()).isEqualTo("날씨 정보를 찾을 수 없습니다.");
            assertThat(result.sources()).isEmpty();
        }
    }

    // ========== AGENT_COMMAND Intent 테스트 ==========

    @Nested
    @DisplayName("generateResponse - AGENT_COMMAND")
    class AgentCommandIntent {

        @Test
        @DisplayName("관리자 Agent 명령 처리")
        void generateResponse_agentCommand_admin() {
            // Given
            ChatRequest request = new ChatRequest("@agent AI 트렌드 분석", null);
            setupCommonMocks(Intent.AGENT_COMMAND);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);
            when(agentDelegationService.delegateToAgent(anyString(), anyLong(), anyString()))
                .thenReturn("Agent 실행 결과입니다.");

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "ADMIN");

            // Then
            assertThat(result.response()).isEqualTo("Agent 실행 결과입니다.");
            verify(agentDelegationService).delegateToAgent(eq("AI 트렌드 분석"), eq(TEST_USER_ID), eq("ADMIN"));
        }

        @Test
        @DisplayName("일반 사용자 Agent 명령 거부")
        void generateResponse_agentCommand_nonAdmin() {
            // Given
            ChatRequest request = new ChatRequest("@agent 작업 실행", null);
            setupCommonMocks(Intent.AGENT_COMMAND);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result.response()).contains("관리자만 사용");
            verify(agentDelegationService, never()).delegateToAgent(anyString(), anyLong(), anyString());
        }
    }

    // ========== 세션 및 메시지 관리 테스트 ==========

    @Nested
    @DisplayName("generateResponse - 세션/메시지 관리")
    class SessionMessageManagement {

        @Test
        @DisplayName("응답 후 메시지 저장")
        void generateResponse_savesMessages() {
            // Given
            ChatRequest request = new ChatRequest("테스트 메시지", null);
            setupCommonMocks(Intent.LLM_DIRECT);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);
            when(llmService.generate(anyList())).thenReturn("테스트 응답");

            // When
            chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            verify(messageService).saveMessage(eq(TEST_SESSION_ID), eq("USER"), eq("테스트 메시지"), anyInt());
            verify(messageService).saveMessage(eq(TEST_SESSION_ID), eq("ASSISTANT"), eq("테스트 응답"), anyInt());
        }

        @Test
        @DisplayName("응답 후 lastMessageAt 업데이트")
        void generateResponse_updatesLastMessageAt() {
            // Given
            ChatRequest request = new ChatRequest("테스트", null);
            setupCommonMocks(Intent.LLM_DIRECT);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);
            when(llmService.generate(anyList())).thenReturn("응답");

            // When
            chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            verify(sessionService).updateLastMessageAt(TEST_SESSION_ID);
        }
    }

    // ========== 타이틀 생성 테스트 ==========

    @Nested
    @DisplayName("generateResponse - 타이틀 생성")
    class TitleGeneration {

        @Test
        @DisplayName("새 세션 시 비동기 타이틀 생성 호출")
        void generateResponse_newSession_타이틀_생성_호출() {
            // Given
            ChatRequest request = new ChatRequest("AI 트렌드 알려줘", null);
            setupCommonMocks(Intent.LLM_DIRECT);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);
            when(llmService.generate(anyList())).thenReturn("AI 트렌드 답변");

            // When
            chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            verify(titleGenerationService).generateAndSaveTitleAsync(
                eq(TEST_SESSION_ID), eq(TEST_USER_ID), eq("AI 트렌드 알려줘"), eq("AI 트렌드 답변"));
        }

        @Test
        @DisplayName("기존 세션 시 타이틀 생성 호출하지 않음")
        void generateResponse_existingSession_타이틀_생성_미호출() {
            // Given
            ChatRequest request = new ChatRequest("이전 대화 이어서", TEST_SESSION_ID);
            setupCommonMocks(Intent.LLM_DIRECT);
            when(messageService.getMessagesForMemory(eq(TEST_SESSION_ID), any())).thenReturn(Collections.emptyList());
            when(llmService.generate(anyList())).thenReturn("네, 이어서 진행합니다.");

            // When
            chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            verify(titleGenerationService, never()).generateAndSaveTitleAsync(
                anyString(), anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("ChatResponse에 title 필드 포함 (null)")
        void generateResponse_chatResponse_title_null() {
            // Given
            ChatRequest request = new ChatRequest("안녕", null);
            setupCommonMocks(Intent.LLM_DIRECT);
            when(sessionService.createSession(TEST_USER_ID.toString(), null)).thenReturn(TEST_SESSION_ID);
            when(llmService.generate(anyList())).thenReturn("안녕하세요!");

            // When
            ChatResponse result = chatbotService.generateResponse(request, TEST_USER_ID, "USER");

            // Then
            assertThat(result.title()).isNull();
        }
    }

    // ========== 헬퍼 메서드 ==========

    private void setupCommonMocks(Intent intent) {
        when(memoryProvider.get(anyString())).thenReturn(chatMemory);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        when(intentService.classifyIntent(anyString())).thenReturn(intent);
        when(tokenService.estimateTokens(anyString())).thenReturn(10);
    }
}
