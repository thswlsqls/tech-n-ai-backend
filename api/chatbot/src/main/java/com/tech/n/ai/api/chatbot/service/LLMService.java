package com.tech.n.ai.api.chatbot.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * LLM 서비스 인터페이스
 */
public interface LLMService {
    
    /**
     * LLM 응답 생성
     * 
     * @param prompt 프롬프트
     * @return LLM 응답
     */
    String generate(String prompt);

    /**
     * 대화 메시지 목록으로 LLM 응답 생성
     * 
     * @param messages 역할이 붙은 대화 메시지 목록
     * @return LLM 응답
     */
    String generate(List<ChatMessage> messages);
}
