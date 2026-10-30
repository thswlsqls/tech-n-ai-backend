package com.tech.n.ai.common.exception.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * ExceptionLoggingService의 @Async 메서드를 활성화
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    // 이름으로만 쓰는 전용 풀이다. 기본 후보에서 빼서 Spring Boot 기본 applicationTaskExecutor가 그대로 만들어지게 한다.
    @Bean(name = "exceptionLoggingExecutor", defaultCandidate = false)
    public Executor exceptionLoggingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("exception-log-");
        executor.initialize();
        return executor;
    }
}
