package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.common.exception.TokenLimitExceededException;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.api.chatbot.service.dto.TokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TokenServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenServiceImpl 단위 테스트")
class TokenServiceImplTest {

    @Mock
    private OpenAiTokenCountEstimator tokenCountEstimator;

    private TokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl(tokenCountEstimator);
        ReflectionTestUtils.setField(tokenService, "maxInputTokens", 4000);
        ReflectionTestUtils.setField(tokenService, "maxOutputTokens", 2000);
        ReflectionTestUtils.setField(tokenService, "warningThreshold", 0.8);
    }

    // ========== estimateTokens 테스트 ==========

    @Nested
    @DisplayName("estimateTokens")
    class EstimateTokens {

        @Test
        @DisplayName("OpenAI estimator 사용")
        void estimateTokens_openai() {
            // Given
            when(tokenCountEstimator.estimateTokenCountInText("Hello World")).thenReturn(2);

            // When
            int result = tokenService.estimateTokens("Hello World");

            // Then
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("null 입력 시 0 반환")
        void estimateTokens_null() {
            assertThat(tokenService.estimateTokens(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("빈 문자열 시 0 반환")
        void estimateTokens_빈문자열() {
            assertThat(tokenService.estimateTokens("")).isEqualTo(0);
        }

        @Test
        @DisplayName("OpenAI estimator 실패 시 휴리스틱 fallback")
        void estimateTokens_fallback() {
            // Given
            when(tokenCountEstimator.estimateTokenCountInText(anyString()))
                .thenThrow(new RuntimeException("estimator 에러"));

            // When
            int result = tokenService.estimateTokens("Hello World");

            // Then
            assertThat(result).isGreaterThan(0);
        }

        @Test
        @DisplayName("estimator null일 때 휴리스틱 사용")
        void estimateTokens_estimatorNull() {
            // Given
            TokenServiceImpl serviceWithoutEstimator = new TokenServiceImpl(null);
            ReflectionTestUtils.setField(serviceWithoutEstimator, "maxInputTokens", 4000);

            // When
            int result = serviceWithoutEstimator.estimateTokens("테스트 입력입니다");

            // Then
            assertThat(result).isGreaterThan(0);
        }

        @Test
        @DisplayName("한국어 텍스트 휴리스틱 추정")
        void estimateTokens_한국어() {
            // Given
            TokenServiceImpl serviceWithoutEstimator = new TokenServiceImpl(null);
            ReflectionTestUtils.setField(serviceWithoutEstimator, "maxInputTokens", 4000);

            // When
            int koreanTokens = serviceWithoutEstimator.estimateTokens("안녕하세요");
            int englishTokens = serviceWithoutEstimator.estimateTokens("Hello");

            // Then - 한국어는 영어보다 토큰 수가 많아야 함
            assertThat(koreanTokens).isGreaterThanOrEqualTo(englishTokens);
        }
    }

    // ========== validateInputTokens 테스트 ==========

    @Nested
    @DisplayName("validateInputTokens")
    class ValidateInputTokens {

        @Test
        @DisplayName("정상 범위 - 예외 없음")
        void validateInputTokens_정상() {
            // Given
            when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(100);

            // When & Then - 예외 발생 안함
            tokenService.validateInputTokens("짧은 프롬프트");
        }

        @Test
        @DisplayName("토큰 초과 시 TokenLimitExceededException")
        void validateInputTokens_초과() {
            // Given
            when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(5000);

            // When & Then
            assertThatThrownBy(() -> tokenService.validateInputTokens("매우 긴 프롬프트"))
                .isInstanceOf(TokenLimitExceededException.class);
        }
    }

    // ========== truncateResults 테스트 ==========

    @Nested
    @DisplayName("truncateResults")
    class TruncateResults {

        @Test
        @DisplayName("토큰 제한 내 모든 결과 포함")
        void truncateResults_모두포함() {
            // Given
            when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(100);
            List<SearchResult> results = List.of(
                SearchResult.builder().documentId("1").text("결과1").score(0.9).build(),
                SearchResult.builder().documentId("2").text("결과2").score(0.8).build()
            );

            // When
            List<SearchResult> truncated = tokenService.truncateResults(results, 1000);

            // Then
            assertThat(truncated).hasSize(2);
        }

        @Test
        @DisplayName("토큰 제한 초과 시 잘라냄")
        void truncateResults_절단() {
            // Given
            when(tokenCountEstimator.estimateTokenCountInText(anyString())).thenReturn(600);
            List<SearchResult> results = List.of(
                SearchResult.builder().documentId("1").text("결과1").score(0.9).build(),
                SearchResult.builder().documentId("2").text("결과2").score(0.8).build(),
                SearchResult.builder().documentId("3").text("결과3").score(0.7).build()
            );

            // When
            List<SearchResult> truncated = tokenService.truncateResults(results, 1000);

            // Then
            assertThat(truncated).hasSize(1);
        }

        @Test
        @DisplayName("빈 리스트 입력 시 빈 리스트 반환")
        void truncateResults_빈리스트() {
            // When
            List<SearchResult> truncated = tokenService.truncateResults(List.of(), 1000);

            // Then
            assertThat(truncated).isEmpty();
        }
    }

    // ========== trackUsage 테스트 ==========

    @Nested
    @DisplayName("trackUsage")
    class TrackUsage {

        @Test
        @DisplayName("토큰 사용량 추적")
        void trackUsage_정상() {
            // When
            TokenUsage usage = tokenService.trackUsage("req-1", "user-1", 100, 50);

            // Then
            assertThat(usage.requestId()).isEqualTo("req-1");
            assertThat(usage.userId()).isEqualTo("user-1");
            assertThat(usage.inputTokens()).isEqualTo(100);
            assertThat(usage.outputTokens()).isEqualTo(50);
            assertThat(usage.totalTokens()).isEqualTo(150);
            assertThat(usage.timestamp()).isNotNull();
        }
    }
}
