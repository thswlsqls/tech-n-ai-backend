package com.tech.n.ai.batch.source.domain.emergingtech.scraper.jobconfig;

import com.tech.n.ai.batch.source.common.Constants;
import com.tech.n.ai.batch.source.domain.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.batch.source.domain.emergingtech.incrementer.EmergingTechJobIncrementer;
import com.tech.n.ai.batch.source.domain.emergingtech.scraper.jobparameter.EmergingTechScraperJobParameter;
import com.tech.n.ai.batch.source.domain.emergingtech.scraper.listener.EmergingTechScraperJobListener;
import com.tech.n.ai.batch.source.domain.emergingtech.scraper.processor.EmergingTechScraperProcessor;
import com.tech.n.ai.batch.source.domain.emergingtech.scraper.reader.EmergingTechScrapingItemReader;
import com.tech.n.ai.batch.source.domain.emergingtech.scraper.service.EmergingTechScraperService;
import com.tech.n.ai.batch.source.domain.emergingtech.scraper.writer.EmergingTechScraperWriter;
import com.tech.n.ai.client.feign.domain.internal.contract.EmergingTechInternalContract;
import com.tech.n.ai.client.scraper.dto.ScrapedTechArticle;
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
 * Emerging Tech 웹 크롤링 수집 Job 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmergingTechScraperJobConfig {

    private static final String JOB_NAME = Constants.EMERGING_TECH_SCRAPER;
    private static final String STEP1_NAME = JOB_NAME + Constants.STEP_1;
    private static final int CHUNK_SIZE = Constants.CHUNK_SIZE_10;

    @Value("${baseDate:#{null}}")
    private String baseDate;

    private final EmergingTechScraperService emergingTechScraperService;
    private final EmergingTechInternalContract emergingTechInternalApi;

    @Bean(name = JOB_NAME + Constants.PARAMETER)
    @JobScope
    public EmergingTechScraperJobParameter parameter() {
        return new EmergingTechScraperJobParameter();
    }

    @Bean(name = JOB_NAME)
    public Job job(JobRepository jobRepository,
                   @Qualifier(STEP1_NAME) Step step1,
                   @Qualifier(JOB_NAME + ".listener") EmergingTechScraperJobListener listener) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(step1)
            .incrementer(new EmergingTechJobIncrementer(baseDate))
            .listener(listener)
            .build();
    }

    @Bean(name = JOB_NAME + ".listener")
    public EmergingTechScraperJobListener jobListener() {
        return new EmergingTechScraperJobListener();
    }

    @Bean(name = STEP1_NAME)
    @JobScope
    public Step step1(JobRepository jobRepository,
                      @Qualifier("primaryPlatformTransactionManager") PlatformTransactionManager transactionManager,
                      @Qualifier(STEP1_NAME + Constants.ITEM_READER) EmergingTechScrapingItemReader reader,
                      @Qualifier(STEP1_NAME + Constants.ITEM_PROCESSOR) EmergingTechScraperProcessor processor,
                      @Qualifier(STEP1_NAME + Constants.ITEM_WRITER) EmergingTechScraperWriter writer) {
        return new StepBuilder(STEP1_NAME, jobRepository)
            .<ScrapedTechArticle, EmergingTechCreateRequest>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_READER)
    @StepScope
    public EmergingTechScrapingItemReader step1Reader() {
        return new EmergingTechScrapingItemReader(CHUNK_SIZE, emergingTechScraperService);
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_PROCESSOR)
    @StepScope
    public EmergingTechScraperProcessor step1Processor() {
        return new EmergingTechScraperProcessor();
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_WRITER)
    @StepScope
    public EmergingTechScraperWriter step1Writer() {
        return new EmergingTechScraperWriter(emergingTechInternalApi);
    }
}
