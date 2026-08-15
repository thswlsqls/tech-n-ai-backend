package com.tech.n.ai.batch.eval.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 평가 잡 두 개
 *
 * searchBaselineEvalJob은 기준선 수치를 재고, goldenSetVerifyJob은 골든셋이 실제 데이터와
 * 맞는지 확인한다. 둘 다 단일 스텝 Tasklet이다.
 */
@Configuration
public class EvalJobConfig {

    public static final String SEARCH_BASELINE_EVAL_JOB = "searchBaselineEvalJob";
    public static final String GOLDEN_SET_VERIFY_JOB = "goldenSetVerifyJob";

    /**
     * JobRepository가 ResourcelessJobRepository라 트랜잭션 매니저도 자원을 잡지 않는 쪽을 쓴다.
     */
    private final PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();

    @Bean(name = SEARCH_BASELINE_EVAL_JOB)
    public Job searchBaselineEvalJob(JobRepository jobRepository,
                                      Step searchBaselineStep,
                                      EmbeddingApiKeyGuardListener guardListener) {
        return new JobBuilder(SEARCH_BASELINE_EVAL_JOB, jobRepository)
            .start(searchBaselineStep)
            .listener(guardListener)
            .build();
    }

    @Bean
    public Step searchBaselineStep(JobRepository jobRepository, SearchBaselineTasklet tasklet) {
        return new StepBuilder("searchBaselineStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }

    @Bean(name = GOLDEN_SET_VERIFY_JOB)
    public Job goldenSetVerifyJob(JobRepository jobRepository, Step goldenSetVerifyStep) {
        return new JobBuilder(GOLDEN_SET_VERIFY_JOB, jobRepository)
            .start(goldenSetVerifyStep)
            .build();
    }

    @Bean
    public Step goldenSetVerifyStep(JobRepository jobRepository, GoldenSetVerifyTasklet tasklet) {
        return new StepBuilder("goldenSetVerifyStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
