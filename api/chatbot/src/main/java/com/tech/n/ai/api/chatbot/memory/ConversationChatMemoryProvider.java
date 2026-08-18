package com.tech.n.ai.api.chatbot.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 대화별 ChatMemory 제공자
 *
 * 메시지 개수를 기준으로 최근 대화만 남기는 창(MessageWindowChatMemory)을 만들어 준다.
 * 창 크기는 chatbot.chat-memory.max-messages 설정으로 정한다.
 *
 * 여기에 MongoDbChatMemoryStore를 걸지 않는 것은 의도한 것이다.
 * MessageWindowChatMemory는 메시지를 자기 안에 들고 있지 않고, add() 할 때마다 store에 쓰고
 * messages() 할 때마다 store에서 다시 읽는다. 그런데 MongoDbChatMemoryStore의
 * updateMessages()와 deleteMessages()는 로그 한 줄만 남기고 아무것도 저장하지 않는다.
 * 그래서 이 store를 걸면 ChatbotServiceImpl.handleGeneralConversation()이 add() 직후 messages()로
 * 만드는 일반 대화 프롬프트에서 현재 질문이 빠진다. RAG·웹검색 경로는 ChatMemory로 프롬프트를
 * 만들지 않아 해당 없다. store를 안 걸면 빌더가 메모리 안에서만 동작하는 store를 대신 만들어 주고,
 * 그래서 이 요청에서 add() 한 현재 질문이 messages()에 그대로 남는다.
 *
 * 이전 대화 이력은 ChatbotServiceImpl.loadHistoryToMemory()가 DB에서 읽어 채운다.
 */
@Component
public class ConversationChatMemoryProvider implements ChatMemoryProvider {

    @Value("${chatbot.chat-memory.max-messages:10}")
    private Integer maxMessages;

    @Override
    public ChatMemory get(Object memoryId) {
        return MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(maxMessages)
            .build();
    }
}
