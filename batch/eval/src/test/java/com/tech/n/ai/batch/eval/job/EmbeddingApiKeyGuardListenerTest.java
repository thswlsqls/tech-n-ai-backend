package com.tech.n.ai.batch.eval.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EmbeddingApiKeyGuardListener 단위 테스트
 */
@DisplayName("EmbeddingApiKeyGuardListener 단위 테스트")
class EmbeddingApiKeyGuardListenerTest {

    private EmbeddingApiKeyGuardListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmbeddingApiKeyGuardListener();
    }

    @Nested
    @DisplayName("beforeJob - API 키 확인")
    class BeforeJob {

        @Test
        @DisplayName("키가 비어 있으면 잡을 시작하지 않는다")
        void blankKey_throws() {
            // Given
            ReflectionTestUtils.setField(listener, "embeddingApiKey", "   ");

            // When & Then
            assertThatThrownBy(() -> listener.beforeJob(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding-model.api-key");
        }

        @Test
        @DisplayName("키가 null이면 잡을 시작하지 않는다")
        void nullKey_throws() {
            // Given
            ReflectionTestUtils.setField(listener, "embeddingApiKey", null);

            // When & Then
            assertThatThrownBy(() -> listener.beforeJob(null))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("키가 있으면 그대로 통과한다")
        void presentKey_passes() {
            // Given
            ReflectionTestUtils.setField(listener, "embeddingApiKey", "sk-test-key");

            // When & Then
            assertThatCode(() -> listener.beforeJob(null)).doesNotThrowAnyException();
        }
    }
}
