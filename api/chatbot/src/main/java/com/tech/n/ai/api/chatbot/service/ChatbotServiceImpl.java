package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.chain.AnswerGenerationChain;
import com.tech.n.ai.api.chatbot.chain.InputInterpretationChain;
import com.tech.n.ai.api.chatbot.chain.ResultRefinementChain;
import com.tech.n.ai.api.chatbot.converter.MessageFormatConverter;
import com.tech.n.ai.api.chatbot.dto.request.ChatRequest;
import com.tech.n.ai.api.chatbot.dto.response.ChatResponse;
import com.tech.n.ai.api.chatbot.dto.response.SourceResponse;
import com.tech.n.ai.api.chatbot.memory.ConversationChatMemoryProvider;
import com.tech.n.ai.common.conversation.service.ConversationMessageService;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import com.tech.n.ai.api.chatbot.service.dto.Intent;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchQuery;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.api.chatbot.service.dto.WebSearchDocument;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {
    
    private final ConversationSessionService sessionService;
    private final ConversationMessageService messageService;
    private final ConversationChatMemoryProvider memoryProvider;
    private final LLMService llmService;
    private final TokenService tokenService;
    private final IntentClassificationService intentService;
    private final InputInterpretationChain inputChain;
    private final VectorSearchService vectorSearchService;
    private final ResultRefinementChain refinementChain;
    private final AnswerGenerationChain answerChain;
    private final WebSearchService webSearchService;
    private final PromptService promptService;

    private final AgentDelegationService agentDelegationService;
    private final MessageFormatConverter messageConverter;
    private final SessionTitleGenerationService titleGenerationService;
    
    @Value("${chatbot.rag.max-search-results:5}")
    private int maxSearchResults;
    
    @Value("${chatbot.rag.min-similarity-score:0.7}")
    private double minSimilarityScore;

    @Value("${chatbot.rag.recency-months:6}")
    private int recencyMonths;
    
    @Override
    public ChatResponse generateResponse(ChatRequest request, Long userId, String userRole) {
        boolean isNewSession = request.conversationId() == null || request.conversationId().isBlank();
        String sessionId = getOrCreateSession(request, userId);
        ChatMemory chatMemory = memoryProvider.get(sessionId);

        if (!isNewSession) {
            loadHistoryToMemory(sessionId, chatMemory);
        }

        Intent intent = intentService.classifyIntent(request.message());
        log.info("Intent classified: {} for message: {}", intent, request.message());

        String response;
        List<SourceResponse> sources;

        switch (intent) {
            case AGENT_COMMAND -> {
                response = handleAgentCommand(request, userId, userRole);
                sources = Collections.emptyList();
            }
            case LLM_DIRECT -> {
                response = handleGeneralConversation(request, sessionId, chatMemory);
                sources = Collections.emptyList();
            }
            case WEB_SEARCH_REQUIRED -> {
                WebSearchResult webResult = handleWebSearchPipeline(request);
                response = webResult.response();
                sources = webResult.sources();
            }
            case RAG_REQUIRED -> {
                RAGResult ragResult = handleRAGPipeline(request, sessionId, userId);
                response = ragResult.response();
                sources = ragResult.sources();
            }
            default -> {
                response = handleGeneralConversation(request, sessionId, chatMemory);
                sources = Collections.emptyList();
            }
        }

        // 현재 메시지만 저장 (히스토리 재로드 없이)
        saveCurrentMessages(sessionId, chatMemory, request.message(), response);

        sessionService.updateLastMessageAt(sessionId);
        trackTokenUsage(sessionId, userId, request.message(), response);

        if (isNewSession) {
            titleGenerationService.generateAndSaveTitleAsync(
                sessionId, userId, request.message(), response);
        }

        return ChatResponse.builder()
            .response(response)
            .conversationId(sessionId)
            .title(null)
            .sources(sources)
            .build();
    }
    
    private String getOrCreateSession(ChatRequest request, Long userId) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            sessionService.getSession(request.conversationId(), userId.toString());
            return request.conversationId();
        }
        return sessionService.createSession(userId.toString(), null);
    }
    
    /**
     * 일반 대화 처리 (chatMemory는 이미 히스토리가 로드된 상태)
     */
    private String handleGeneralConversation(ChatRequest request, String sessionId, ChatMemory chatMemory) {
        UserMessage userMessage = UserMessage.from(request.message());
        chatMemory.add(userMessage);

        List<ChatMessage> messages = chatMemory.messages();
        Object providerFormat = messageConverter.convertToProviderFormat(messages, null);
        String response = llmService.generate(providerFormat.toString());

        AiMessage aiMessage = AiMessage.from(response);
        chatMemory.add(aiMessage);

        return response;
    }
    
    /**
     * Agent 명령 처리
     */
    private String handleAgentCommand(ChatRequest request, Long userId, String userRole) {
        if (!"ADMIN".equals(userRole)) {
            return "Agent 명령은 관리자만 사용할 수 있습니다. 일반 질문은 '@agent' 없이 메시지를 보내주세요.";
        }

        String goal = request.message().substring("@agent".length()).trim();
        return agentDelegationService.delegateToAgent(goal, userId, userRole);
    }

    private RAGResult handleRAGPipeline(ChatRequest request, String sessionId, Long userId) {
        SearchQuery searchQuery = inputChain.interpret(request.message());
        SearchOptions searchOptions = buildSearchOptions(searchQuery);

        List<SearchResult> searchResults =
            vectorSearchService.search(searchQuery.query(), userId, searchOptions);
        log.info("RAG search completed: {} results for query: {}", searchResults.size(), searchQuery.query());

        boolean recencyDetected = searchQuery.context().isRecencyDetected();
        boolean scoreFusionApplied = Boolean.TRUE.equals(searchOptions.enableScoreFusion());
        List<SearchResult> refinedResults = refinementChain.refine(
            request.message(), searchResults, recencyDetected, scoreFusionApplied);
        log.info("RAG refinement completed: {} -> {} results (recencyDetected={}, scoreFusionApplied={})",
            searchResults.size(), refinedResults.size(), recencyDetected, scoreFusionApplied);

        String response = answerChain.generate(request.message(), refinedResults);

        List<SourceResponse> sources = refinedResults.stream()
            .map(r -> {
                String title = null;
                String url = null;
                if (r.metadata() instanceof org.bson.Document doc) {
                    title = doc.getString("title");
                    url = doc.getString("url");
                }
                return SourceResponse.builder()
                    .documentId(r.documentId())
                    .collectionType(r.collectionType())
                    .score(r.score())
                    .title(title)
                    .url(url)
                    .build();
            })
            .collect(Collectors.toList());

        log.info("RAG sources count: {}", sources.size());
        sources.forEach(s -> log.info("RAG source: title={}, url={}, score={}, type={}",
            s.title(), s.url(), s.score(), s.collectionType()));

        return new RAGResult(response, sources);
    }
    
    private SearchOptions buildSearchOptions(SearchQuery searchQuery) {
        boolean recency = searchQuery.context().isRecencyDetected();
        LocalDateTime dateFrom = recency ? LocalDateTime.now().minusMonths(recencyMonths) : null;

        return SearchOptions.builder()
            .includeEmergingTechs(searchQuery.context().includesEmergingTechs())
            .maxResults(maxSearchResults)
            .minSimilarityScore(minSimilarityScore)
            .providerFilters(searchQuery.context().getDetectedProviders())
            .updateTypeFilters(searchQuery.context().getDetectedUpdateTypes())
            .recencyDetected(recency)
            .dateFrom(dateFrom)
            .enableScoreFusion(true)
            .build();
    }
    
    /**
     * 히스토리를 ChatMemory에 로드 (한 번만 호출)
     */
    private void loadHistoryToMemory(String sessionId, ChatMemory chatMemory) {
        List<ChatMessage> history = messageService.getMessagesForMemory(sessionId, null);
        history.forEach(chatMemory::add);
    }

    /**
     * 현재 메시지만 저장 (히스토리 재로드 없이)
     */
    private void saveCurrentMessages(String sessionId, ChatMemory chatMemory,
                                      String userMessage, String assistantMessage) {
        // ChatMemory에는 이미 handleGeneralConversation에서 추가됨
        // RAG 경로의 경우에만 여기서 추가
        // 중복 추가 방지를 위해 현재 메시지가 이미 있는지 확인
        List<ChatMessage> messages = chatMemory.messages();
        boolean alreadyAdded = messages.size() >= 2 &&
            messages.get(messages.size() - 2) instanceof UserMessage &&
            messages.get(messages.size() - 1) instanceof AiMessage;

        if (!alreadyAdded) {
            chatMemory.add(UserMessage.from(userMessage));
            chatMemory.add(AiMessage.from(assistantMessage));
        }

        // DB에 저장
        messageService.saveMessage(sessionId, "USER", userMessage,
            tokenService.estimateTokens(userMessage));
        messageService.saveMessage(sessionId, "ASSISTANT", assistantMessage,
            tokenService.estimateTokens(assistantMessage));
    }
    
    private void trackTokenUsage(String sessionId, Long userId, String input, String output) {
        int inputTokens = tokenService.estimateTokens(input);
        int outputTokens = tokenService.estimateTokens(output);
        tokenService.trackUsage(sessionId, userId.toString(), inputTokens, outputTokens);
    }
    
    /**
     * Web 검색 파이프라인 처리
     */
    private WebSearchResult handleWebSearchPipeline(ChatRequest request) {
        List<WebSearchDocument> webResults = webSearchService.search(request.message());

        if (webResults.isEmpty()) {
            String fallbackResponse = llmService.generate(request.message());
            return new WebSearchResult(fallbackResponse, Collections.emptyList());
        }

        String prompt = promptService.buildWebSearchPrompt(request.message(), webResults);
        String response = llmService.generate(prompt);

        List<SourceResponse> sources = webResults.stream()
            .map(doc -> SourceResponse.builder()
                .title(doc.title())
                .url(doc.url())
                .build())
            .collect(Collectors.toList());

        return new WebSearchResult(response, sources);
    }

    private record RAGResult(String response, List<SourceResponse> sources) {}
    private record WebSearchResult(String response, List<SourceResponse> sources) {}
}
