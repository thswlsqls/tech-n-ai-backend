package com.tech.n.ai.common.conversation.service;

import com.tech.n.ai.common.conversation.dto.MessageResponse;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 대화 메시지 서비스 인터페이스
 */
public interface ConversationMessageService {

    /**
     * 메시지 저장
     *
     * @param sessionId 세션 ID (TSID String)
     * @param role 메시지 역할 (USER, ASSISTANT, SYSTEM)
     * @param content 메시지 내용
     * @param tokenCount 토큰 수 (선택)
     */
    void saveMessage(String sessionId, String role, String content, Integer tokenCount);

    /**
     * 세션의 메시지 목록 조회 (페이징)
     *
     * @param sessionId 세션 ID (TSID String)
     * @param pageable 페이징 정보
     * @return 메시지 목록
     */
    Page<MessageResponse> getMessages(String sessionId, Pageable pageable);

    /**
     * ChatMemory용 메시지 조회 (토큰 제한 고려)
     *
     * @param sessionId 세션 ID (TSID String)
     * @param maxTokens 최대 토큰 수 (null이면 제한 없음)
     * @return ChatMessage 리스트
     */
    List<ChatMessage> getMessagesForMemory(String sessionId, Integer maxTokens);
}
