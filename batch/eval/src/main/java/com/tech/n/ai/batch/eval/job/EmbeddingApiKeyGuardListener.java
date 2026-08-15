package com.tech.n.ai.batch.eval.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 임베딩 API 키가 비어 있으면 잡을 시작하지 않는다.
 *
 * 키 없이 돌리면 검색이 전부 실패해 recall 0짜리 리포트가 정상 결과처럼 남는다.
 */
@Slf4j
@Component
public class EmbeddingApiKeyGuardListener implements JobExecutionListener {

    @Value("${langchain4j.open-ai.embedding-model.api-key:}")
    private String embeddingApiKey;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            throw new IllegalStateException(
                "langchain4j.open-ai.embedding-model.api-key가 비어 있다. 임베딩 없이는 검색 품질을 잴 수 없다.");
        }
        log.info("Embedding API key check passed");
    }
}
