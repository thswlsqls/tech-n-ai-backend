package com.tech.n.ai.batch.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.boot.SpringApplication;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BatchSourceApplication 진입점 단위 테스트
 *
 * SpringApplication의 모든 static 호출이 인자를 기록하고 표시용 예외를 던지게 한다.
 * 그래서 main은 첫 호출인 run에서 멈추고 System.exit까지 가지 않는다.
 */
@DisplayName("BatchSourceApplication 단위 테스트")
class BatchSourceApplicationTest {

    @Nested
    @DisplayName("main - 명령줄 인자 전달")
    class Main {

        @Test
        @DisplayName("명령줄 인자를 그대로 SpringApplication.run에 넘긴다")
        void passesArgsToRun() {
            // Given
            String[] args = {"--job.name=emerging-tech.github.job", "--baseDate=2026-10-27"};
            List<InvocationOnMock> calls = new ArrayList<>();

            try (MockedStatic<SpringApplication> ignored =
                     Mockito.mockStatic(SpringApplication.class, invocation -> {
                         calls.add(invocation);
                         throw new StaticCallIntercepted();
                     })) {

                // When
                assertThatThrownBy(() -> BatchSourceApplication.main(args))
                    .isInstanceOf(StaticCallIntercepted.class);
            }

            // Then
            assertThat(calls).hasSize(1);
            InvocationOnMock run = calls.get(0);
            assertThat(run.getMethod().getName()).isEqualTo("run");
            Object[] rawArguments = run.getRawArguments();
            assertThat(rawArguments[0]).isEqualTo(BatchSourceApplication.class);
            assertThat((String[]) rawArguments[1])
                .containsExactly("--job.name=emerging-tech.github.job", "--baseDate=2026-10-27");
        }
    }

    private static class StaticCallIntercepted extends RuntimeException {
    }
}
