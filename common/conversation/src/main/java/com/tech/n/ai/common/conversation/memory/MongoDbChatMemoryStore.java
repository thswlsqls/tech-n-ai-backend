package com.tech.n.ai.common.conversation.memory;

import com.tech.n.ai.domain.mongodb.document.ConversationMessageDocument;
import com.tech.n.ai.domain.mongodb.repository.ConversationMessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MongoDB 기반 ChatMemoryStore 구현
 *
 * langchain4j ChatMemoryStore 인터페이스를 구현하여
 * MongoDB에 저장된 대화 이력을 ChatMemory에 통합합니다.
 *
 * <p>getMessages()는 최근 MAX_LOAD_MESSAGES개만 조회하여
 * 세션이 길어져도 MongoDB I/O 비용이 무한 증가하지 않도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoDbChatMemoryStore implements ChatMemoryStore {

    private final ConversationMessageRepository conversationMessageRepository;

    /**
     * MongoDB에서 로드할 최대 메시지 수.
     * agent(30), chatbot(10) 중 큰 값을 커버하도록 설정.
     */
    private static final int MAX_LOAD_MESSAGES = 50;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();

        // 최근 MAX_LOAD_MESSAGES개를 역순(최신 순)으로 조회
        PageRequest pageRequest = PageRequest.of(0, MAX_LOAD_MESSAGES,
            Sort.by(Sort.Direction.DESC, "sequence_number"));
        List<ConversationMessageDocument> documents = conversationMessageRepository
            .findBySessionId(sessionId, pageRequest)
            .getContent();

        // 역순 조회했으므로 다시 오름차순으로 정렬
        List<ConversationMessageDocument> sorted = new ArrayList<>(documents);
        sorted.sort((a, b) -> Integer.compare(a.getSequenceNumber(), b.getSequenceNumber()));

        return sorted.stream()
            .map(this::toChatMessage)
            .collect(Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 메시지 업데이트는 ConversationMessageService를 통해 수행됨
        // ChatMemoryStore 인터페이스 구현을 위해 no-op으로 처리
        log.debug("updateMessages called for memoryId={}, messages count={}. " +
            "Message persistence is handled by ConversationMessageService.", memoryId, messages.size());
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 메시지 삭제는 세션 삭제 시 ConversationSessionService를 통해 수행됨
        log.debug("deleteMessages called for memoryId={}. " +
            "Message deletion is handled by ConversationSessionService.", memoryId);
    }

    /**
     * 메시지 조회 (String sessionId 편의 메서드)
     */
    public List<ChatMessage> getMessages(String sessionId) {
        return getMessages((Object) sessionId);
    }

    /**
     * Document를 ChatMessage로 변환
     */
    private ChatMessage toChatMessage(ConversationMessageDocument doc) {
        String role = doc.getRole();
        String content = doc.getContent();

        return switch (role) {
            case "SYSTEM" -> new SystemMessage(content);
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AiMessage(content);
            default -> throw new IllegalArgumentException("Unknown message role: " + role);
        };
    }
}
