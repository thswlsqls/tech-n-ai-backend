package com.tech.n.ai.batch.graph.job;

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
 * 그래프 구축 잡
 *
 * 표본 실행과 전건 실행이 같은 잡이다. --graph.build.document-limit 값만 달라진다.
 */
@Configuration
public class GraphBuildJobConfig {

    public static final String TECH_GRAPH_BUILD_JOB = "techGraphBuildJob";

    /**
     * JobRepository가 ResourcelessJobRepository라 트랜잭션 매니저도 자원을 잡지 않는 쪽을 쓴다.
     */
    private final PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();

    @Bean(name = TECH_GRAPH_BUILD_JOB)
    public Job techGraphBuildJob(JobRepository jobRepository,
                                 Step graphBuildStep,
                                 GraphBuildJobListener guardListener) {
        return new JobBuilder(TECH_GRAPH_BUILD_JOB, jobRepository)
            .start(graphBuildStep)
            .listener(guardListener)
            .build();
    }

    @Bean
    public Step graphBuildStep(JobRepository jobRepository, GraphBuildTasklet tasklet) {
        return new StepBuilder("graphBuildStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
