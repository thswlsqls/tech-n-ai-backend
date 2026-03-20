package com.tech.n.ai.api.agent.service;

import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Agent 세션 타이틀 생성 서비스 구현체
 *
 * 첫 메시지-응답 쌍을 기반으로 LLM을 호출하여 3~5단어의 세션 타이틀을 비동기 생성합니다.
 */
@Slf4j
@Service
public class SessionTitleGenerationServiceImpl implements SessionTitleGenerationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_INPUT_LENGTH = 300;

    private final ChatModel chatModel;
    private final ConversationSessionService sessionService;

    public SessionTitleGenerationServiceImpl(
            @Qualifier("agentChatModel") ChatModel chatModel,
            ConversationSessionService sessionService) {
        this.chatModel = chatModel;
        this.sessionService = sessionService;
    }

    @Async
    @Override
    public void generateAndSaveTitleAsync(String sessionId, String userId,
                                           String userMessage, String aiResponse) {
        try {
            String title = generateTitle(userMessage, aiResponse);
            if (title != null) {
                sessionService.updateSessionTitle(sessionId, userId, title);
                log.info("세션 타이틀 생성 완료: sessionId={}, title={}", sessionId, title);
            }
        } catch (Exception e) {
            log.warn("세션 타이틀 생성 실패: sessionId={}", sessionId, e);
        }
    }

    private String generateTitle(String userMessage, String aiResponse) {
        String prompt = buildTitlePrompt(userMessage, aiResponse);
        String rawTitle = chatModel.chat(prompt);
        return sanitizeTitle(rawTitle);
    }

    private String buildTitlePrompt(String userMessage, String aiResponse) {
        return """
            ---BEGIN Conversation---
            User: %s
            Assistant: %s
            ---END Conversation---

            Generate a concise title (3-5 words) that captures the main topic of this conversation.
            Write the title in the same language as the user's message.
            Do not use quotation marks, special formatting, or emojis.
            Respond with only the title text, nothing else.""".formatted(
                truncate(userMessage, MAX_INPUT_LENGTH),
                truncate(aiResponse, MAX_INPUT_LENGTH)
            );
    }

    private String sanitizeTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return null;
        }
        String title = rawTitle.strip()
            .replaceAll("^\"|\"$", "")
            .replaceAll("^'|'$", "");

        if (title.isBlank()) {
            return null;
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
