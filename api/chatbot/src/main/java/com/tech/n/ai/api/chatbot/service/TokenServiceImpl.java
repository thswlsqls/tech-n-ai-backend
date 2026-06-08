package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.common.exception.TokenLimitExceededException;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.api.chatbot.service.dto.TokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 토큰 서비스 구현체
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    // 한글 음절 영역 (가 ~ 힣)
    private static final int HANGUL_SYLLABLE_START = 0xAC00;
    private static final int HANGUL_SYLLABLE_END = 0xD7A3;
    // 휴리스틱 토큰 가중치
    private static final double KOREAN_TOKENS_PER_CHAR = 2;
    private static final double ENGLISH_TOKENS_PER_WORD = 1.3;
    private static final int CHARS_PER_TOKEN = 4;

    private final OpenAiTokenCountEstimator tokenCountEstimator;

    @Value("${chatbot.token.max-input-tokens:4000}")
    private int maxInputTokens;

    @Value("${chatbot.token.max-output-tokens:2000}")
    private int maxOutputTokens;

    @Value("${chatbot.token.warning-threshold:0.8}")
    private double warningThreshold;

    public TokenServiceImpl(@Autowired(required = false) OpenAiTokenCountEstimator tokenCountEstimator) {
        this.tokenCountEstimator = tokenCountEstimator;
    }
    
    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // OpenAiTokenCountEstimator가 있으면 사용, 없으면 fallback
        if (tokenCountEstimator != null) {
            try {
                return tokenCountEstimator.estimateTokenCountInText(text);
            } catch (Exception e) {
                log.warn("OpenAiTokenCountEstimator failed, falling back to heuristic: {}", e.getMessage());
                return estimateTokensHeuristic(text);
            }
        }

        return estimateTokensHeuristic(text);
    }

    /**
     * Fallback: 휴리스틱 기반 토큰 추정
     */
    private int estimateTokensHeuristic(String text) {
        int wordCount = text.split("\\s+").length;
        int koreanCharCount = (int) text.chars()
            .filter(c -> c >= HANGUL_SYLLABLE_START && c <= HANGUL_SYLLABLE_END)
            .count();

        // 한국어 문자는 약 2 토큰, 영어 단어는 약 1.3 토큰
        int estimatedTokens = (int) (koreanCharCount * KOREAN_TOKENS_PER_CHAR
            + (wordCount - koreanCharCount) * ENGLISH_TOKENS_PER_WORD);
        return Math.max(estimatedTokens, text.length() / CHARS_PER_TOKEN);
    }
    
    @Override
    public void validateInputTokens(String prompt) {
        int tokenCount = estimateTokens(prompt);
        
        if (tokenCount > maxInputTokens) {
            throw new TokenLimitExceededException(
                String.format("입력 토큰 수(%d)가 최대 허용 토큰 수(%d)를 초과했습니다.", 
                    tokenCount, maxInputTokens)
            );
        }
        
        if (tokenCount > maxInputTokens * warningThreshold) {
            log.warn("입력 토큰 수가 경고 임계값을 초과했습니다: {}/{}", 
                tokenCount, maxInputTokens);
        }
    }
    
    @Override
    public List<SearchResult> truncateResults(List<SearchResult> results, int maxTokens) {
        List<SearchResult> truncated = new ArrayList<>();
        int currentTokens = 0;
        
        for (SearchResult result : results) {
            int resultTokens = estimateTokens(result.text());
            if (currentTokens + resultTokens > maxTokens) {
                break;
            }
            truncated.add(result);
            currentTokens += resultTokens;
        }
        
        return truncated;
    }
    
    @Override
    public TokenUsage trackUsage(String requestId, String userId, int inputTokens, int outputTokens) {
        TokenUsage usage = TokenUsage.builder()
            .requestId(requestId)
            .userId(userId)
            .inputTokens(inputTokens)
            .outputTokens(outputTokens)
            .totalTokens(inputTokens + outputTokens)
            .timestamp(Instant.now())
            .build();
        
        // 로깅
        log.info("Token usage tracked: requestId={}, userId={}, inputTokens={}, outputTokens={}, totalTokens={}",
            requestId, userId, inputTokens, outputTokens, usage.totalTokens());
        
        return usage;
    }
}
