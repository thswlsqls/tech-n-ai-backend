package com.tech.n.ai.common.exception.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AsyncConfig")
class AsyncConfigTest {

    private static final long TIMEOUT_SECONDS = 5;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncConfig.class, AsyncProbeConfig.class);

    @Nested
    @DisplayName("Spring Boot 기본 풀")
    class DefaultExecutor {

        @Test
        @DisplayName("AsyncConfig가 있어도 applicationTaskExecutor가 만들어지고 이름 없는 @Async는 task- 스레드에서 실행된다")
        void 이름_없는_Async는_applicationTaskExecutor에서_실행된다() {
            contextRunner.run(context -> {
                // Given
                AsyncProbe probe = context.getBean(AsyncProbe.class);

                // When
                String threadName = probe.runWithDefaultExecutor().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // Then
                assertThat(context).hasBean("applicationTaskExecutor");
                assertThat(threadName).startsWith("task-");
            });
        }

        @Test
        @DisplayName("@EnableScheduling으로 taskScheduler가 함께 있어도 이름 없는 @Async는 task- 스레드에서 실행된다")
        void 스케줄러가_있어도_이름_없는_Async는_applicationTaskExecutor에서_실행된다() {
            contextRunner
                    .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                    .withUserConfiguration(SchedulingConfig.class)
                    .run(context -> {
                        // Given
                        AsyncProbe probe = context.getBean(AsyncProbe.class);

                        // When
                        String threadName = probe.runWithDefaultExecutor().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                        // Then
                        assertThat(context).hasBean("taskScheduler");
                        assertThat(threadName).startsWith("task-");
                    });
        }
    }

    @Nested
    @DisplayName("exceptionLoggingExecutor")
    class ExceptionLoggingExecutor {

        @Test
        @DisplayName("@Async(\"exceptionLoggingExecutor\")는 exception-log- 스레드에서 실행된다")
        void 이름을_지정한_Async는_exceptionLoggingExecutor에서_실행된다() {
            contextRunner.run(context -> {
                // Given
                AsyncProbe probe = context.getBean(AsyncProbe.class);

                // When
                String threadName = probe.runWithExceptionLoggingExecutor().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // Then
                assertThat(threadName).startsWith("exception-log-");
            });
        }

        @Test
        @DisplayName("풀 설정은 core 2, max 5, queue 100, 접두사 exception-log-를 유지한다")
        void 풀_설정을_유지한다() {
            contextRunner.run(context -> {
                // Given
                ThreadPoolTaskExecutor executor =
                        context.getBean("exceptionLoggingExecutor", ThreadPoolTaskExecutor.class);

                // When & Then
                assertThat(executor.getCorePoolSize()).isEqualTo(2);
                assertThat(executor.getMaxPoolSize()).isEqualTo(5);
                assertThat(executor.getQueueCapacity()).isEqualTo(100);
                assertThat(executor.getThreadNamePrefix()).isEqualTo("exception-log-");
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AsyncProbeConfig {

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfig {
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<String> runWithDefaultExecutor() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }

        @Async("exceptionLoggingExecutor")
        public CompletableFuture<String> runWithExceptionLoggingExecutor() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
