package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 의도 분류 서비스 구현체
 */
@Slf4j
@Service
public class IntentClassificationServiceImpl implements IntentClassificationService {

    private static final Set<String> GREETING_KEYWORDS = Set.of(
        "안녕", "안녕하세요", "하이", "hi", "hello", "헬로"
    );

    // 한국어 키워드는 조사가 붙어 단어 경계가 생기지 않으므로 부분 문자열(contains)로 찾는다
    private static final Set<String> RAG_KEYWORDS_KO = Set.of(
        "대회", "뉴스", "기사",
        "검색", "찾아", "알려", "정보", "어떤", "무엇",
        // Emerging Tech 키워드
        "인공지능", "모델", "릴리즈", "업데이트",
        "기술", "트렌드", "동향",
        "출시", "발표", "버전"
    );

    // 영어 키워드는 단어 경계로 찾는다 — contains로 찾으면 "ai"가 explain·email·available·plain 안에 걸린다
    private static final Pattern RAG_KEYWORDS_EN = Pattern.compile(
        "\\b(contest|news|kaggle|codeforces|leetcode|hackathon"
            + "|ai|llm|gpt|claude|gemini|api|sdk|release|update"
            + "|openai|anthropic|google|meta|xai|tech|trend)\\b"
    );

    // Web 검색이 필요한 최신/실시간 정보 키워드
    private static final Set<String> WEB_SEARCH_KEYWORDS = Set.of(
        "오늘", "현재", "지금", "최근", "today", "now", "latest", "current",
        "날씨", "weather", "주가", "stock", "환율", "exchange rate",
        "뉴스 속보", "breaking news", "실시간",
        "검색해줘", "찾아줘", "인터넷에서"
    );

    // 창작/텍스트 처리 요청 키워드 (LLM 직접 처리)
    private static final Set<String> LLM_DIRECT_KEYWORDS = Set.of(
        "작성해줘", "만들어줘", "써줘", "번역", "요약", "설명해줘",
        "write", "create", "translate", "summarize", "explain"
    );

    private static final String AGENT_COMMAND_PREFIX = "@agent";

    @Override
    public Intent classifyIntent(String preprocessedInput) {
        String lowerInput = preprocessedInput.toLowerCase().trim();

        // 0. @agent 프리픽스 감지 (명시적 명령만)
        if (lowerInput.startsWith(AGENT_COMMAND_PREFIX)) {
            log.info("Intent: AGENT_COMMAND - {}", truncateForLog(preprocessedInput));
            return Intent.AGENT_COMMAND;
        }

        // 1. RAG 키워드 체크 (기술 관련 최신 정보는 벡터 검색 우선)
        if (containsRagKeywords(lowerInput)) {
            log.info("Intent: RAG_REQUIRED - {}", truncateForLog(preprocessedInput));
            return Intent.RAG_REQUIRED;
        }

        // 2. Web 검색 키워드 체크
        if (containsWebSearchKeywords(lowerInput)) {
            log.info("Intent: WEB_SEARCH_REQUIRED - {}", truncateForLog(preprocessedInput));
            return Intent.WEB_SEARCH_REQUIRED;
        }

        // 3. 질문 형태 체크 (RAG 관련 질문일 가능성)
        if (isQuestion(lowerInput) && !containsLlmDirectKeywords(lowerInput)) {
            log.info("Intent: RAG_REQUIRED (question) - {}", truncateForLog(preprocessedInput));
            return Intent.RAG_REQUIRED;
        }

        // 4. 기본값: LLM 직접 처리
        log.info("Intent: LLM_DIRECT - {}", truncateForLog(preprocessedInput));
        return Intent.LLM_DIRECT;
    }

    private boolean isGreeting(String input) {
        return GREETING_KEYWORDS.stream().anyMatch(input::contains);
    }

    private boolean containsRagKeywords(String input) {
        return RAG_KEYWORDS_KO.stream().anyMatch(input::contains)
            || RAG_KEYWORDS_EN.matcher(input).find();
    }

    private boolean containsWebSearchKeywords(String input) {
        return WEB_SEARCH_KEYWORDS.stream().anyMatch(input::contains);
    }

    private boolean containsLlmDirectKeywords(String input) {
        return LLM_DIRECT_KEYWORDS.stream().anyMatch(input::contains);
    }

    private boolean isQuestion(String input) {
        // 의문사 체크 (구어체 포함)
        boolean hasQuestionWords = input.matches(
            ".*(무엇|어떤|어디|언제|누가|왜|어떻게|뭐|몇|얼마|어느).*"
        );
        boolean hasQuestionMark = input.contains("?") || input.contains("？");
        return hasQuestionWords || hasQuestionMark;
    }

    private String truncateForLog(String input) {
        return input.length() > 50 ? input.substring(0, 50) + "..." : input;
    }
}
