package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.batch.eval.goldenset.GoldenSet;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItem;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetLoader;
import com.tech.n.ai.batch.eval.report.EvalReport;
import com.tech.n.ai.batch.eval.report.EvalReportWriter;
import com.tech.n.ai.batch.eval.scoring.AggregateMetrics;
import com.tech.n.ai.batch.eval.scoring.AggregateScorer;
import com.tech.n.ai.batch.eval.scoring.QuestionOutcome;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 골든셋 전체를 돌려 기준선 리포트를 만든다.
 */
@Slf4j
@Component
public class SearchBaselineTasklet implements Tasklet {

    private static final String SCHEMA_VERSION = "1";
    private static final String COLLECTION_EMERGING_TECHS = "emerging_techs";
    private static final String TOKEN_ESTIMATION = "OpenAiTokenCountEstimator (추정치, 실측 아님)";

    private final GoldenSetLoader goldenSetLoader;
    private final QuestionRunner questionRunner;
    private final EvalReportWriter reportWriter;
    private final MongoTemplate mongoTemplate;

    @Value("${chatbot.rag.max-search-results:5}")
    private int maxSearchResults;

    @Value("${chatbot.rag.min-similarity-score:0.7}")
    private double minSimilarityScore;

    @Value("${chatbot.rag.recency-months:6}")
    private int recencyMonths;

    @Value("${chatbot.reranking.enabled:false}")
    private boolean rerankingEnabled;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private double chatModelTemperature;

    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Value("${langchain4j.open-ai.embedding-model.dimensions:1536}")
    private int embeddingDimensions;

    public SearchBaselineTasklet(GoldenSetLoader goldenSetLoader,
                                  QuestionRunner questionRunner,
                                  EvalReportWriter reportWriter,
                                  MongoTemplate mongoTemplate) {
        this.goldenSetLoader = goldenSetLoader;
        this.questionRunner = questionRunner;
        this.reportWriter = reportWriter;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime executedAt = LocalDateTime.now();
        GoldenSet goldenSet = goldenSetLoader.load();

        List<EvalReport.Question> questions = new ArrayList<>();
        List<QuestionOutcome> byVectorRank = new ArrayList<>();
        List<QuestionOutcome> byFusionRank = new ArrayList<>();
        List<QuestionOutcome> byChainOutput = new ArrayList<>();

        for (GoldenSetItem item : goldenSet.items()) {
            QuestionRunResult result = questionRunner.run(item);
            questions.add(result.question());
            byVectorRank.add(result.byVectorRank());
            byFusionRank.add(result.byFusionRank());
            byChainOutput.add(result.byChainOutput());
        }

        AggregateMetrics vectorRankMetrics = AggregateScorer.aggregate(byVectorRank, QuestionRunner.K_VALUES);

        EvalReport report = new EvalReport(
            SCHEMA_VERSION,
            executedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            goldenSet.version(),
            config(),
            questions,
            new EvalReport.Aggregate(
                toBlock(vectorRankMetrics),
                toBlock(AggregateScorer.aggregate(byFusionRank, QuestionRunner.K_VALUES)),
                toBlock(AggregateScorer.aggregate(byChainOutput, QuestionRunner.K_VALUES))
            ),
            // 제외 판정은 순위 기준과 무관해 세 블록이 모두 같다
            new EvalReport.Excluded(
                vectorRankMetrics.excluded().intentNotRag(),
                vectorRankMetrics.excluded().fallbackPath(),
                vectorRankMetrics.excluded().searchFailed(),
                vectorRankMetrics.excluded().noEvidenceType()
            )
        );

        reportWriter.write(report, executedAt);
        return RepeatStatus.FINISHED;
    }

    private EvalReport.Config config() {
        return new EvalReport.Config(
            maxSearchResults,
            minSimilarityScore,
            recencyMonths,
            true,
            rerankingEnabled,
            chatModelTemperature,
            embeddingModelName,
            embeddingDimensions,
            publishedDocumentCount(),
            TOKEN_ESTIMATION,
            false
        );
    }

    private long publishedDocumentCount() {
        Query query = new Query(Criteria.where("status").is("PUBLISHED"));
        return mongoTemplate.count(query, Document.class, COLLECTION_EMERGING_TECHS);
    }

    private EvalReport.AggregateBlock toBlock(AggregateMetrics metrics) {
        return new EvalReport.AggregateBlock(
            metrics.totalCount(),
            metrics.scoredCount(),
            metrics.recallAtK(),
            metrics.hitRateAtK(),
            metrics.mrr(),
            metrics.zeroHitQuestionCount(),
            metrics.falsePositiveAtK(),
            metrics.scoredCountByType(),
            metrics.recencyLatestHitRateAt5(),
            new EvalReport.NoEvidenceSummary(
                metrics.noEvidence().total(),
                metrics.noEvidence().correctlyEmpty(),
                metrics.noEvidence().wronglyNonEmpty()
            )
        );
    }
}
