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
    Excluded excluded
) {

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
    ) {}

    /**
     * "근거 없음" 유형 질문의 판정 결과. 다른 유형이면 null
     */
    public record NoEvidence(
        boolean candidatesEmpty,
        boolean correct
    ) {}

    /**
     * 구간별 소요 시간(ms). 생성 모델을 부르지 않으므로 generation은 항상 null
     */
    public record LatencyMs(
        Long search,
        Long refine,
        Long generation
    ) {}

    /**
     * 토큰 사용량. 생성 모델을 부르지 않으므로 출력 토큰과 호출 수는 0
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
}
