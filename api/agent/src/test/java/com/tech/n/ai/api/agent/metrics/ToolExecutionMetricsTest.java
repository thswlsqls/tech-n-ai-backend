package com.tech.n.ai.api.agent.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolExecutionMetrics 단위 테스트
 */
@DisplayName("ToolExecutionMetrics 단위 테스트")
class ToolExecutionMetricsTest {

    private ToolExecutionMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new ToolExecutionMetrics();
    }

    // ========== incrementToolCall 테스트 ==========

    @Nested
    @DisplayName("incrementToolCall")
    class IncrementToolCall {

        @Test
        @DisplayName("초기값 0에서 시작")
        void incrementToolCall_초기값() {
            assertThat(metrics.getToolCallCount()).isZero();
        }

        @Test
        @DisplayName("increment 후 1 증가")
        void incrementToolCall_증가() {
            metrics.incrementToolCall();
            assertThat(metrics.getToolCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("여러 번 increment 후 누적 증가")
        void incrementToolCall_누적() {
            metrics.incrementToolCall();
            metrics.incrementToolCall();
            metrics.incrementToolCall();
            assertThat(metrics.getToolCallCount()).isEqualTo(3);
        }
    }

    // ========== incrementAnalyticsCall 테스트 ==========

    @Nested
    @DisplayName("incrementAnalyticsCall")
    class IncrementAnalyticsCall {

        @Test
        @DisplayName("초기값 0에서 시작")
        void incrementAnalyticsCall_초기값() {
            assertThat(metrics.getAnalyticsCallCount()).isZero();
        }

        @Test
        @DisplayName("increment 후 1 증가")
        void incrementAnalyticsCall_증가() {
            metrics.incrementAnalyticsCall();
            assertThat(metrics.getAnalyticsCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("여러 번 increment 후 누적 증가")
        void incrementAnalyticsCall_누적() {
            metrics.incrementAnalyticsCall();
            metrics.incrementAnalyticsCall();
            assertThat(metrics.getAnalyticsCallCount()).isEqualTo(2);
        }
    }

    // ========== incrementValidationError 테스트 ==========

    @Nested
    @DisplayName("incrementValidationError")
    class IncrementValidationError {

        @Test
        @DisplayName("초기값 0에서 시작")
        void incrementValidationError_초기값() {
            assertThat(metrics.getValidationErrorCount()).isZero();
        }

        @Test
        @DisplayName("increment 후 1 증가")
        void incrementValidationError_증가() {
            metrics.incrementValidationError();
            assertThat(metrics.getValidationErrorCount()).isEqualTo(1);
        }
    }

    // ========== 스레드 안전성 테스트 ==========

    @Nested
    @DisplayName("스레드 안전성")
    class ThreadSafety {

        @Test
        @DisplayName("동시 increment 호출 시 정확한 카운트")
        void threadSafety_동시호출() throws InterruptedException {
            // Given
            int threadCount = 100;
            int incrementsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < incrementsPerThread; j++) {
                            metrics.incrementToolCall();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(metrics.getToolCallCount()).isEqualTo(threadCount * incrementsPerThread);
        }

        @Test
        @DisplayName("모든 카운터 동시 증가 시 정확한 값")
        void threadSafety_모든카운터() throws InterruptedException {
            // Given
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        metrics.incrementToolCall();
                        metrics.incrementAnalyticsCall();
                        metrics.incrementValidationError();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(metrics.getToolCallCount()).isEqualTo(threadCount);
            assertThat(metrics.getAnalyticsCallCount()).isEqualTo(threadCount);
            assertThat(metrics.getValidationErrorCount()).isEqualTo(threadCount);
        }
    }

    // ========== 독립성 테스트 ==========

    @Nested
    @DisplayName("카운터 독립성")
    class CounterIndependence {

        @Test
        @DisplayName("각 카운터가 독립적으로 동작")
        void countersAreIndependent() {
            // Given & When
            metrics.incrementToolCall();
            metrics.incrementToolCall();
            metrics.incrementAnalyticsCall();

            // Then
            assertThat(metrics.getToolCallCount()).isEqualTo(2);
            assertThat(metrics.getAnalyticsCallCount()).isEqualTo(1);
            assertThat(metrics.getValidationErrorCount()).isZero();
        }
    }
}
