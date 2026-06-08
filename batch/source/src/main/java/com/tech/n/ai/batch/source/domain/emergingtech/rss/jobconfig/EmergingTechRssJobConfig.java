package com.tech.n.ai.batch.source.domain.emergingtech.rss.jobconfig;

import com.tech.n.ai.batch.source.common.Constants;
import com.tech.n.ai.batch.source.domain.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.batch.source.domain.emergingtech.incrementer.EmergingTechJobIncrementer;
import com.tech.n.ai.batch.source.domain.emergingtech.rss.jobparameter.EmergingTechRssJobParameter;
import com.tech.n.ai.batch.source.domain.emergingtech.rss.listener.EmergingTechRssJobListener;
import com.tech.n.ai.batch.source.domain.emergingtech.rss.processor.EmergingTechRssProcessor;
import com.tech.n.ai.batch.source.domain.emergingtech.rss.reader.EmergingTechRssPagingItemReader;
import com.tech.n.ai.batch.source.domain.emergingtech.rss.service.EmergingTechRssService;
import com.tech.n.ai.batch.source.domain.emergingtech.rss.writer.EmergingTechRssWriter;
import com.tech.n.ai.client.feign.domain.internal.contract.EmergingTechInternalContract;
import com.tech.n.ai.client.rss.dto.RssFeedItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Emerging Tech RSS 수집 Job 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmergingTechRssJobConfig {

    private static final String JOB_NAME = Constants.EMERGING_TECH_RSS;
    private static final String STEP1_NAME = JOB_NAME + Constants.STEP_1;
    private static final int CHUNK_SIZE = Constants.CHUNK_SIZE_10;

    @Value("${baseDate:#{null}}")
    private String baseDate;

    private final EmergingTechRssService emergingTechRssService;
    private final EmergingTechInternalContract emergingTechInternalApi;

    @Bean(name = JOB_NAME + Constants.PARAMETER)
    @JobScope
    public EmergingTechRssJobParameter parameter() {
        return new EmergingTechRssJobParameter();
    }

    @Bean(name = JOB_NAME)
    public Job job(JobRepository jobRepository,
                   @Qualifier(STEP1_NAME) Step step1,
                   @Qualifier(JOB_NAME + ".listener") EmergingTechRssJobListener listener) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(step1)
            .incrementer(new EmergingTechJobIncrementer(baseDate))
            .listener(listener)
            .build();
    }

    @Bean(name = JOB_NAME + ".listener")
    public EmergingTechRssJobListener jobListener() {
        return new EmergingTechRssJobListener();
    }

    @Bean(name = STEP1_NAME)
    @JobScope
    public Step step1(JobRepository jobRepository,
                      @Qualifier("primaryPlatformTransactionManager") PlatformTransactionManager transactionManager,
                      @Qualifier(STEP1_NAME + Constants.ITEM_READER) EmergingTechRssPagingItemReader reader,
                      @Qualifier(STEP1_NAME + Constants.ITEM_PROCESSOR) EmergingTechRssProcessor processor,
                      @Qualifier(STEP1_NAME + Constants.ITEM_WRITER) EmergingTechRssWriter writer) {
        return new StepBuilder(STEP1_NAME, jobRepository)
            .<RssFeedItem, EmergingTechCreateRequest>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_READER)
    @StepScope
    public EmergingTechRssPagingItemReader step1Reader() {
        return new EmergingTechRssPagingItemReader(CHUNK_SIZE, emergingTechRssService);
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_PROCESSOR)
    @StepScope
    public EmergingTechRssProcessor step1Processor() {
        return new EmergingTechRssProcessor();
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_WRITER)
    @StepScope
    public EmergingTechRssWriter step1Writer() {
        return new EmergingTechRssWriter(emergingTechInternalApi);
    }
}
