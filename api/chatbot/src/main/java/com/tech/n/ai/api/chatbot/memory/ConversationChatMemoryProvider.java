package com.tech.n.ai.api.chatbot.memory;

import com.tech.n.ai.common.conversation.memory.MongoDbChatMemoryStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 대화별 ChatMemory 제공자
 * 
 * 토큰 수 기준 메시지 유지 방식(TokenWindowChatMemory)을 기본 전략으로 사용합니다.
 * 이는 토큰 제한 준수, 비용 통제, Provider별 컨텍스트 길이 제한 준수를 보장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationChatMemoryProvider implements ChatMemoryProvider {
    
    private final MongoDbChatMemoryStore memoryStore;
    
    @Value("${chatbot.chat-memory.max-tokens:2000}")
    private Integer maxTokens;
    
    // TODO: TokenCountEstimator Bean 추가 필요 (ChatMemory 구현 시점에 추가)
    // private final TokenCountEstimator tokenCountEstimator;
    
    @Override
    public ChatMemory get(Object memoryId) {
        String sessionId = (String) memoryId;
        
        // TODO: TokenWindowChatMemory 사용 (TokenCountEstimator Bean 필요)
        // 현재는 MessageWindowChatMemory로 대체 (TokenCountEstimator 없이 동작)
        // TokenCountEstimator Bean 추가 후 TokenWindowChatMemory로 변경 필요
        log.warn("Using MessageWindowChatMemory as fallback. TokenWindowChatMemory requires TokenCountEstimator Bean.");
        
        // 임시로 MessageWindowChatMemory 사용 (TokenCountEstimator 없이 동작)
        // TODO: ChatMemoryStore 인터페이스 확인 후 연결
        return MessageWindowChatMemory.builder()
            .id(sessionId)
            .maxMessages(10)  // 임시값
            .build();
        
        // TokenCountEstimator Bean 추가 후 아래 코드로 변경:
        // return TokenWindowChatMemory.builder()
        //     .id(sessionId)
        //     .maxTokens(maxTokens)
        //     .chatMemoryStore(memoryStore)
        //     .tokenCountEstimator(tokenCountEstimator)
        //     .build();
    }
}
