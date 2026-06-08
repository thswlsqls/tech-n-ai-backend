package com.tech.n.ai.batch.source.domain.emergingtech.incrementer;

import com.tech.n.ai.batch.source.common.incrementer.UniqueRunIdIncrementer;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;

/**
 * Emerging Tech 수집 Job 공용 Incrementer (GitHub·RSS·스크래퍼 공유)
 */
public class EmergingTechJobIncrementer extends RunIdIncrementer {

    private static final String RUN_ID_KEY = "run.id";
    private static final String BASE_DATE_KEY = "baseDate";

    private final String baseDate;

    public EmergingTechJobIncrementer(String baseDate) {
        this.baseDate = baseDate;
    }

    @Override
    public JobParameters getNext(JobParameters parameters) {
        JobParameters params = parameters == null ? new JobParameters() : parameters;
        long nextRunId = UniqueRunIdIncrementer.safeGetLong(params, RUN_ID_KEY, 0L) + 1;

        return new JobParametersBuilder()
            .addLong(RUN_ID_KEY, nextRunId)
            .addString(BASE_DATE_KEY, baseDate != null ? baseDate : "")
            .toJobParameters();
    }
}
