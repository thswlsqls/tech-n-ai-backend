package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.api.chatbot.chain.AnswerGenerationChain;
import com.tech.n.ai.api.chatbot.service.PromptService;
import com.tech.n.ai.api.chatbot.service.TokenService;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.batch.eval.config.JudgeModelConfig;
import com.tech.n.ai.batch.eval.goldenset.GoldenSet;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItem;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetLoader;
import com.tech.n.ai.batch.eval.judge.AnswerJudge;
import com.tech.n.ai.batch.eval.judge.JudgeVerdict;
import com.tech.n.ai.batch.eval.report.EvalConfigSnapshotFactory;
import com.tech.n.ai.batch.eval.report.EvalReport;
import com.tech.n.ai.batch.eval.report.EvalReportWriter;
import com.tech.n.ai.batch.eval.scoring.AggregateMetrics;
import com.tech.n.ai.batch.eval.scoring.AggregateScorer;
import com.tech.n.ai.batch.eval.scoring.ExternalIdExtractor;
import com.tech.n.ai.batch.eval.scoring.QuestionOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 골든셋 질문을 운영 경로로 태워 답변을 만들고 두 축으로 채점한다.
 *
 * 검색 부분은 QuestionRunner에 맡기고, 제외 사유가 없는 질문에만 답변을 만들어 판정 모델에 넘긴다.
 * 리포트의 aggregate는 채우지 않고 null로 둔다. 이 잡은 골든셋 일부만 돌릴 수 있어
 * 여기서 낸 recall을 검색 기준선 잡의 수치와 짝지어 읽으면 잘못된 비교가 되기 때문이다.
 */
@Slf4j
@Component
public class AnswerQualityTasklet implements Tasklet {

    private static final String REPORT_FILE_PREFIX = "answer-quality-";
    private static final String SKIPPED_JUDGE_CALL_LIMIT = "JUDGE_CALL_LIMIT";
    private static final String SKIPPED_GENERATION_FAILED = "GENERATION_FAILED";
    private static final int AXIS_COUNT = 2;

    private final GoldenSetLoader goldenSetLoader;
    private final QuestionRunner questionRunner;
    private final AnswerGenerationChain answerChain;
    private final PromptService promptService;
    private final TokenService tokenService;
    private final AnswerJudge answerJudge;
    private final EvalConfigSnapshotFactory configSnapshotFactory;
    private final EvalReportWriter reportWriter;
    private final int judgeCallLimit;
    private final int questionLimit;
    private final boolean measureJudgeFlip;
    private final int maxContextTokens;
    private final String judgeModelName;

    public AnswerQualityTasklet(GoldenSetLoader goldenSetLoader,
                                 QuestionRunner questionRunner,
                                 AnswerGenerationChain answerChain,
                                 PromptService promptService,
                                 TokenService tokenService,
                                 AnswerJudge answerJudge,
                                 EvalConfigSnapshotFactory configSnapshotFactory,
                                 EvalReportWriter reportWriter,
                                 @Value("${eval.answer-quality.judge-call-limit:200}") int judgeCallLimit,
                                 @Value("${eval.answer-quality.question-limit:0}") int questionLimit,
                                 @Value("${eval.answer-quality.measure-judge-flip:false}") boolean measureJudgeFlip,
                                 @Value("${chatbot.rag.max-context-tokens:3000}") int maxContextTokens,
                                 @Value("${eval.judge.model-name:gpt-4o}") String judgeModelName) {
        this.goldenSetLoader = goldenSetLoader;
        this.questionRunner = questionRunner;
        this.answerChain = answerChain;
        this.promptService = promptService;
        this.tokenService = tokenService;
        this.answerJudge = answerJudge;
        this.configSnapshotFactory = configSnapshotFactory;
        this.reportWriter = reportWriter;
        this.judgeCallLimit = judgeCallLimit;
        this.questionLimit = questionLimit;
        this.measureJudgeFlip = measureJudgeFlip;
        this.maxContextTokens = maxContextTokens;
        this.judgeModelName = judgeModelName;
    }

    /** 판정까지 끝낸 질문 하나의 재료. 판정 뒤집힘 측정에서 같은 프롬프트를 다시 쓰려고 들고 있는다 */
    private record JudgedQuestion(String question, String answer, List<SearchResult> evidence,
                                   JudgeVerdict groundedness, JudgeVerdict answerRelevance) {}

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime executedAt = LocalDateTime.now();
        GoldenSet goldenSet = goldenSetLoader.load();
        List<GoldenSetItem> items = limitItems(goldenSet.items());

        List<GoldenSetItem> ranItems = new ArrayList<>();
        List<QuestionRunResult> runResults = new ArrayList<>();
        List<EvalReport.Question> questions = new ArrayList<>();
        List<QuestionOutcome> outcomes = new ArrayList<>();

        for (GoldenSetItem item : items) {
            QuestionRunResult result = questionRunner.run(item);
            ranItems.add(item);
            runResults.add(result);
            questions.add(result.question());
            outcomes.add(result.byVectorRank());
        }

        List<EvalReport.AnswerQualityQuestion> judged = new ArrayList<>();
        List<JudgedQuestion> flipCandidates = new ArrayList<>();
        int judgeCallCount = 0;
        boolean judgeCallLimitReached = false;

        for (int i = 0; i < ranItems.size(); i++) {
            GoldenSetItem item = ranItems.get(i);
            QuestionRunResult result = runResults.get(i);
            EvalReport.Question question = result.question();

            if (question.excludedReason() != null) {
                judged.add(skipped(item.id(), question.excludedReason()));
                continue;
            }
            if (judgeCallLimitReached || judgeCallCount + AXIS_COUNT > judgeCallLimit) {
                judgeCallLimitReached = true;
                judged.add(skipped(item.id(), SKIPPED_JUDGE_CALL_LIMIT));
                continue;
            }

            // 채점에 넘길 근거는 운영 프롬프트가 실제로 담는 만큼으로 자른다
            List<SearchResult> evidence = tokenService.truncateResults(result.refined(), maxContextTokens);
            List<String> evidenceExternalIds = evidence.stream()
                .map(r -> ExternalIdExtractor.from(r).orElse(r.documentId()))
                .toList();

            String answer;
            long generationMs;
            int inputTokens;
            try {
                // buildPrompt가 질문당 두 번 돈다. 토큰을 세려면 프롬프트 문자열이 필요한데
                // 이 호출은 API를 부르지 않고 같은 입력이면 같은 결과라 비용도 편차도 없다.
                String prompt = promptService.buildPrompt(item.question(), result.refined());
                inputTokens = tokenService.estimateTokens(prompt);

                long generationStart = System.currentTimeMillis();
                answer = answerChain.generate(item.question(), result.refined());
                generationMs = System.currentTimeMillis() - generationStart;
            } catch (RuntimeException e) {
                // 토큰 상한 초과도 여기로 들어온다. 한 질문 때문에 잡 전체를 멈추지 않는다.
                log.warn("답변 생성 실패: id={}, message={}", item.id(), e.getMessage());
                judged.add(new EvalReport.AnswerQualityQuestion(
                    item.id(), null, evidence.size(), evidenceExternalIds,
                    null, "답변 생성 실패: " + e.getMessage(),
                    null, "답변 생성 실패: " + e.getMessage(),
                    SKIPPED_GENERATION_FAILED));
                continue;
            }

            questions.set(i, question.withGeneration(
                new EvalReport.LatencyMs(
                    question.latencyMs().search(), question.latencyMs().refine(), generationMs,
                    question.latencyMs().graph()),
                new EvalReport.Tokens(inputTokens, tokenService.estimateTokens(answer), 1)));

            JudgeVerdict groundedness =
                answerJudge.judge(AnswerJudge.Axis.GROUNDEDNESS, item.question(), answer, evidence);
            JudgeVerdict answerRelevance =
                answerJudge.judge(AnswerJudge.Axis.ANSWER_RELEVANCE, item.question(), answer, evidence);
            judgeCallCount += AXIS_COUNT;

            judged.add(new EvalReport.AnswerQualityQuestion(
                item.id(), answer, evidence.size(), evidenceExternalIds,
                groundedness.score(), groundedness.reason(),
                answerRelevance.score(), answerRelevance.reason(),
                null));

            if (groundedness.parsed() && answerRelevance.parsed()) {
                flipCandidates.add(new JudgedQuestion(
                    item.question(), answer, evidence, groundedness, answerRelevance));
            }
        }

        // 측정을 껐어도 키는 남긴다. 같은 잡의 두 실행을 나란히 놓고 비교할 때
        // 리포트 구조가 설정에 따라 달라지면 안 되기 때문이다.
        EvalReport.JudgeFlip flip = new EvalReport.JudgeFlip(0, 0, null);
        if (measureJudgeFlip) {
            FlipResult flipResult = measureFlip(flipCandidates, judgeCallCount);
            flip = flipResult.flip();
            judgeCallCount = flipResult.judgeCallCount();
            judgeCallLimitReached = judgeCallLimitReached || flipResult.limitReached();
        }

        AggregateMetrics.Excluded excluded =
            AggregateScorer.aggregate(outcomes, QuestionRunner.K_VALUES).excluded();

        EvalReport report = new EvalReport(
            EvalReport.SCHEMA_VERSION,
            executedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            goldenSet.version(),
            configSnapshotFactory.create(true),
            questions,
            null,
            new EvalReport.Excluded(
                excluded.intentNotRag(),
                excluded.fallbackPath(),
                excluded.searchFailed(),
                excluded.noEvidenceType()),
            new EvalReport.AnswerQuality(
                judgeModelName,
                JudgeModelConfig.JUDGE_TEMPERATURE,
                judgeCallLimit,
                judgeCallCount,
                judgeCallLimitReached,
                questionLimit,
                judged,
                axis(judged, EvalReport.AnswerQualityQuestion::groundedness),
                axis(judged, EvalReport.AnswerQualityQuestion::answerRelevance),
                flip)
        );

        reportWriter.write(report, executedAt, REPORT_FILE_PREFIX);
        return RepeatStatus.FINISHED;
    }

    private List<GoldenSetItem> limitItems(List<GoldenSetItem> items) {
        if (questionLimit <= 0) {
            return items;
        }
        return items.subList(0, Math.min(questionLimit, items.size()));
    }

    private EvalReport.AnswerQualityQuestion skipped(String id, String reason) {
        return new EvalReport.AnswerQualityQuestion(
            id, null, 0, List.of(), null, null, null, null, reason);
    }

    /** 뒤집힘 측정 결과와 그때까지 쓴 판정 호출 수 */
    private record FlipResult(EvalReport.JudgeFlip flip, int judgeCallCount, boolean limitReached) {}

    /**
     * 1차 판정이 끝난 뒤 같은 프롬프트로 한 번 더 채점해 점수가 갈리는 비율을 잰다.
     * 상한은 1차 판정과 함께 쓴다. 집계에 들어가는 점수는 1차 결과 그대로다.
     */
    private FlipResult measureFlip(List<JudgedQuestion> candidates, int judgeCallCount) {
        int sampledCount = 0;
        int flippedCount = 0;
        boolean limitReached = false;
        int calls = judgeCallCount;

        for (JudgedQuestion candidate : candidates) {
            if (calls + AXIS_COUNT > judgeCallLimit) {
                limitReached = true;
                break;
            }
            JudgeVerdict groundedness = answerJudge.judge(
                AnswerJudge.Axis.GROUNDEDNESS, candidate.question(), candidate.answer(), candidate.evidence());
            JudgeVerdict answerRelevance = answerJudge.judge(
                AnswerJudge.Axis.ANSWER_RELEVANCE, candidate.question(), candidate.answer(), candidate.evidence());
            calls += AXIS_COUNT;

            if (groundedness.parsed()) {
                sampledCount++;
                if (!groundedness.score().equals(candidate.groundedness().score())) {
                    flippedCount++;
                }
            }
            if (answerRelevance.parsed()) {
                sampledCount++;
                if (!answerRelevance.score().equals(candidate.answerRelevance().score())) {
                    flippedCount++;
                }
            }
        }

        Double flipRate = sampledCount == 0 ? null : (double) flippedCount / sampledCount;
        return new FlipResult(new EvalReport.JudgeFlip(sampledCount, flippedCount, flipRate), calls, limitReached);
    }

    private EvalReport.AnswerQualityAxis axis(List<EvalReport.AnswerQualityQuestion> judged,
                                               Function<EvalReport.AnswerQualityQuestion, Integer> scoreOf) {
        int denominator = 0;
        int passCount = 0;
        int parseFailedCount = 0;

        for (EvalReport.AnswerQualityQuestion question : judged) {
            if (question.skippedReason() != null) {
                continue;
            }
            Integer score = scoreOf.apply(question);
            if (score == null) {
                parseFailedCount++;
                continue;
            }
            denominator++;
            if (score == 1) {
                passCount++;
            }
        }

        Double passRate = denominator == 0 ? null : (double) passCount / denominator;
        return new EvalReport.AnswerQualityAxis(denominator, passCount, passRate, parseFailedCount);
    }
}
