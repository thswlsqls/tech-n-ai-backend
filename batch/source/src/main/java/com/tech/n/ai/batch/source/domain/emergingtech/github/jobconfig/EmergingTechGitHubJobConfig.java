package com.tech.n.ai.batch.source.domain.emergingtech.github.jobconfig;

import com.tech.n.ai.batch.source.common.Constants;
import com.tech.n.ai.batch.source.domain.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.batch.source.domain.emergingtech.incrementer.EmergingTechJobIncrementer;
import com.tech.n.ai.batch.source.domain.emergingtech.github.jobparameter.EmergingTechGitHubJobParameter;
import com.tech.n.ai.batch.source.domain.emergingtech.github.listener.EmergingTechGitHubJobListener;
import com.tech.n.ai.batch.source.domain.emergingtech.github.processor.GitHubReleasesProcessor;
import com.tech.n.ai.batch.source.domain.emergingtech.github.reader.GitHubReleaseWithRepo;
import com.tech.n.ai.batch.source.domain.emergingtech.github.reader.GitHubReleasesPagingItemReader;
import com.tech.n.ai.batch.source.domain.emergingtech.github.service.GitHubReleasesService;
import com.tech.n.ai.batch.source.domain.emergingtech.github.writer.GitHubReleasesWriter;
import com.tech.n.ai.client.feign.domain.internal.contract.EmergingTechInternalContract;
import com.tech.n.ai.domain.mongodb.enums.TechProvider;
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

import java.util.List;

/**
 * Emerging Tech GitHub Releases Job 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmergingTechGitHubJobConfig {

    private static final String JOB_NAME = Constants.EMERGING_TECH_GITHUB;
    private static final String STEP1_NAME = JOB_NAME + Constants.STEP_1;
    private static final int CHUNK_SIZE = Constants.CHUNK_SIZE_10;

    @Value("${baseDate:#{null}}")
    private String baseDate;

    private final GitHubReleasesService gitHubReleasesService;
    private final EmergingTechInternalContract emergingTechInternalApi;

    // 대상 저장소 목록
    private static final List<GitHubReleasesPagingItemReader.RepositoryInfo> TARGET_REPOSITORIES = List.of(
        new GitHubReleasesPagingItemReader.RepositoryInfo("openai", "openai-python", TechProvider.OPENAI.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("openai", "whisper", TechProvider.OPENAI.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("openai", "tiktoken", TechProvider.OPENAI.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("anthropics", "anthropic-sdk-python", TechProvider.ANTHROPIC.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("anthropics", "claude-code", TechProvider.ANTHROPIC.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("google", "generative-ai-python", TechProvider.GOOGLE.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("google", "gemma.cpp", TechProvider.GOOGLE.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("google-deepmind", "gemma", TechProvider.GOOGLE.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("meta-llama", "llama-models", TechProvider.META.name()),
        new GitHubReleasesPagingItemReader.RepositoryInfo("meta-llama", "llama-stack", TechProvider.META.name())
    );

    @Bean(name = JOB_NAME + Constants.PARAMETER)
    @JobScope
    public EmergingTechGitHubJobParameter parameter() {
        return new EmergingTechGitHubJobParameter();
    }

    @Bean(name = JOB_NAME)
    public Job job(JobRepository jobRepository,
                   @Qualifier(STEP1_NAME) Step step1,
                   @Qualifier(JOB_NAME + ".listener") EmergingTechGitHubJobListener listener) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(step1)
            .incrementer(new EmergingTechJobIncrementer(baseDate))
            .listener(listener)
            .build();
    }

    @Bean(name = JOB_NAME + ".listener")
    public EmergingTechGitHubJobListener jobListener() {
        return new EmergingTechGitHubJobListener();
    }

    @Bean(name = STEP1_NAME)
    @JobScope
    public Step step1(JobRepository jobRepository,
                      @Qualifier("primaryPlatformTransactionManager") PlatformTransactionManager transactionManager,
                      @Qualifier(STEP1_NAME + Constants.ITEM_READER) GitHubReleasesPagingItemReader reader,
                      @Qualifier(STEP1_NAME + Constants.ITEM_PROCESSOR) GitHubReleasesProcessor processor,
                      @Qualifier(STEP1_NAME + Constants.ITEM_WRITER) GitHubReleasesWriter writer) {
        return new StepBuilder(STEP1_NAME, jobRepository)
            .<GitHubReleaseWithRepo, EmergingTechCreateRequest>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_READER)
    @StepScope
    public GitHubReleasesPagingItemReader step1Reader() {
        return new GitHubReleasesPagingItemReader(CHUNK_SIZE, gitHubReleasesService, TARGET_REPOSITORIES);
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_PROCESSOR)
    @StepScope
    public GitHubReleasesProcessor step1Processor() {
        return new GitHubReleasesProcessor();
    }

    @Bean(name = STEP1_NAME + Constants.ITEM_WRITER)
    @StepScope
    public GitHubReleasesWriter step1Writer() {
        return new GitHubReleasesWriter(emergingTechInternalApi);
    }
}
