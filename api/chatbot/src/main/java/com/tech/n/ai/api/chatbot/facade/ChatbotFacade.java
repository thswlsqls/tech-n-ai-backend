package com.tech.n.ai.api.chatbot.facade;

import com.tech.n.ai.api.chatbot.dto.request.ChatRequest;
import com.tech.n.ai.api.chatbot.dto.response.*;
import com.tech.n.ai.api.chatbot.service.ChatbotService;
import com.tech.n.ai.common.conversation.dto.MessageResponse;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
import com.tech.n.ai.common.conversation.service.ConversationMessageService;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import com.tech.n.ai.common.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotFacade {

    private final ChatbotService chatbotService;
    private final ConversationSessionService conversationSessionService;
    private final ConversationMessageService conversationMessageService;

    public ChatResponse chat(ChatRequest request, Long userId, String userRole) {
        return chatbotService.generateResponse(request, userId, userRole);
    }

    public SessionListResponse listSessions(Long userId, int page, int size, Pageable pageable) {
        Page<SessionResponse> sessionPage = conversationSessionService.listSessions(userId.toString(), pageable);

        List<SessionResponse> list = sessionPage.getContent();

        PageData<SessionResponse> pageData = PageData.of(
            size,
            page,
            (int) sessionPage.getTotalElements(),
            list
        );

        return SessionListResponse.from(pageData);
    }

    public MessageListResponse listMessages(String sessionId, Long userId, int page, int size, Pageable pageable) {
        conversationSessionService.getSession(sessionId, userId.toString());

        Page<MessageResponse> messagePage = conversationMessageService.getMessages(sessionId, pageable);

        List<MessageResponse> list = messagePage.getContent();

        PageData<MessageResponse> pageData = PageData.of(
            size,
            page,
            (int) messagePage.getTotalElements(),
            list
        );

        return MessageListResponse.from(pageData);
    }
}
