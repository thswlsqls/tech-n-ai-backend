package com.tech.n.ai.batch.eval.report;

import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;
import com.tech.n.ai.batch.eval.scoring.RankedCandidate;
import com.tech.n.ai.batch.eval.scoring.RetrievalMetrics;

import java.util.List;
import java.util.Map;

/**
 * 기준선 실행 리포트
 *
 * 키 집합은 고정이다. 값이 없어도 키를 빼지 않고 null·0·빈 배열을 넣는다.
 * 03·04 실행과 나란히 놓고 비교할 수 있어야 하기 때문이다.
 */
public record EvalReport(
    String schemaVersion,
    String executedAt,
    String goldenSetVersion,
    Config config,
    List<Question> questions,
    Aggregate aggregate,
    Excluded excluded,
    AnswerQuality answerQuality
) {

    /** answerQuality 블록이 생기면서 올린 버전. 앞선 실행의 리포트와 구분한다 */
    public static final String SCHEMA_VERSION = "2";

    /**
     * 실행 당시 설정 스냅샷
     */
    public record Config(
        int maxSearchResults,
        double minSimilarityScore,
        int recencyMonths,
        boolean enableScoreFusion,
        boolean rerankingEnabled,
        Double chatModelTemperature,
        String embeddingModelName,
        Integer embeddingDimensions,
        long publishedDocumentCount,
        String tokenEstimation,
        boolean generationModelCalled
    ) {}

    /**
     * 질문 한 건의 실행 기록
     */
    public record Question(
        String id,
        GoldenSetItemType type,
        String question,
        String intent,
        String searchPath,
        boolean recencyQueryFailed,
        boolean scored,
        String excludedReason,
        NoEvidence noEvidence,
        LatencyMs latencyMs,
        Tokens tokens,
        List<String> expectedExternalIds,
        String latestExternalId,
        List<RankedCandidate> candidates,
        List<ChainOutputItem> chainOutput,
        Metrics metrics
    ) {

        /**
         * 검색까지 기록해 둔 항목에 답변 생성 구간의 시간과 토큰을 채워 넣는다.
         * 답변 품질 잡은 검색을 QuestionRunner에 맡기고 생성만 따로 하므로 두 결과를 합칠 자리가 필요하다.
         */
        public Question withGeneration(LatencyMs latencyMs, Tokens tokens) {
            return new Question(
                id, type, question, intent, searchPath, recencyQueryFailed, scored, excludedReason,
                noEvidence, latencyMs, tokens, expectedExternalIds, latestExternalId,
                candidates, chainOutput, metrics);
        }
    }

    /**
     * "근거 없음" 유형 질문의 판정 결과. 다른 유형이면 null
     */
    public record NoEvidence(
        boolean candidatesEmpty,
        boolean correct
    ) {}

    /**
     * 구간별 소요 시간(ms). 검색 기준선 잡은 생성 모델을 부르지 않아 generation이 null이고,
     * 답변 품질 잡은 답변을 만들면서 잰 시간을 채운다
     */
    public record LatencyMs(
        Long search,
        Long refine,
        Long generation
    ) {}

    /**
     * 토큰 사용량. 검색 기준선 잡은 생성 모델을 부르지 않아 출력 토큰과 호출 수가 0이고,
     * 답변 품질 잡은 답변을 만든 만큼 채운다
     */
    public record Tokens(
        int inputTokens,
        int outputTokens,
        int llmCallCount
    ) {}

    /**
     * 정제 체인이 최종적으로 남긴 문서
     */
    public record ChainOutputItem(
        String externalId,
        String documentId,
        int rank,
        Double score,
        boolean expected
    ) {}

    /**
     * 같은 질문을 세 기준으로 각각 채점한 결과
     */
    public record Metrics(
        RetrievalMetrics byVectorRank,
        RetrievalMetrics byFusionRank,
        RetrievalMetrics byChainOutput
    ) {}

    /**
     * 집계도 질문 항목과 같은 세 기준으로 낸다
     */
    public record Aggregate(
        AggregateBlock byVectorRank,
        AggregateBlock byFusionRank,
        AggregateBlock byChainOutput
    ) {}

    /**
     * 기준 하나에 대한 집계 수치. 제외 버킷은 기준과 무관해 최상위 excluded에 따로 둔다
     */
    public record AggregateBlock(
        int totalCount,
        int scoredCount,
        Map<Integer, Double> recallAtK,
        Map<Integer, Double> hitRateAtK,
        double mrr,
        int zeroHitQuestionCount,
        Map<Integer, Double> falsePositiveAtK,
        Map<GoldenSetItemType, Integer> scoredCountByType,
        Double recencyLatestHitRateAt5,
        NoEvidenceSummary noEvidence
    ) {}

    /**
     * 집계에서 뺀 질문 수. 판정 순서는 intentNotRag → fallbackPath → searchFailed → noEvidenceType
     */
    public record Excluded(
        int intentNotRag,
        int fallbackPath,
        int searchFailed,
        int noEvidenceType
    ) {}

    public record NoEvidenceSummary(
        int total,
        int correctlyEmpty,
        int wronglyNonEmpty
    ) {}

    /**
     * 답변 품질 잡만 채운다. 검색 기준선 잡은 이 블록을 null로 남긴다.
     */
    public record AnswerQuality(
        String judgeModelName,
        double judgeModelTemperature,
        int judgeCallLimit,
        int judgeCallCount,
        boolean judgeCallLimitReached,
        int questionLimit,
        List<AnswerQualityQuestion> questions,
        AnswerQualityAxis groundedness,
        AnswerQualityAxis answerRelevance,
        JudgeFlip flip
    ) {}

    /**
     * 질문 한 건의 답변과 두 축 판정. 판정을 못 한 질문은 점수가 null이고 skippedReason에 이유가 남는다
     */
    public record AnswerQualityQuestion(
        String id,
        String answer,
        int evidenceCount,
        List<String> evidenceExternalIds,
        Integer groundedness,
        String groundednessReason,
        Integer answerRelevance,
        String answerRelevanceReason,
        String skippedReason
    ) {}

    /**
     * 축 하나의 집계. 분모는 판정 모델의 응답을 제대로 읽은 건수다
     */
    public record AnswerQualityAxis(
        int denominator,
        int passCount,
        Double passRate,
        int parseFailedCount
    ) {}

    /**
     * 같은 답변을 두 번 채점했을 때 점수가 갈리는 비율.
     * 집계에 쓰는 점수는 1차 결과이고 2차는 판정이 얼마나 흔들리는지 재는 용도다.
     * 측정을 끄면 건수는 0, flipRate는 null이다
     */
    public record JudgeFlip(
        int sampledCount,
        int flippedCount,
        Double flipRate
    ) {}
}
