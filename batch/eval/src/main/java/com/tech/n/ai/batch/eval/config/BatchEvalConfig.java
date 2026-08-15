package com.tech.n.ai.batch.eval.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;

/**
 * 배치 기본 설정
 *
 * 실행 이력을 남기지 않는다. @EnableBatchProcessing 기본값인 ResourcelessJobRepository를 쓰고
 * JDBC JobRepository는 붙이지 않는다. 실행 기록은 타임스탬프가 붙은 리포트 JSON이 대신한다.
 */
@Configuration
@EnableBatchProcessing
public class BatchEvalConfig {

}
