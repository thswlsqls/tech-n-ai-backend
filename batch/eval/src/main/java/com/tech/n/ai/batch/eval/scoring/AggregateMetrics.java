package com.tech.n.ai.batch.eval.scoring;

import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;

import java.util.Map;

/**
 * 골든셋 전체 집계
 *
 * @param totalCount 골든셋 질문 수
 * @param scoredCount 제외 버킷을 빼고 실제로 채점한 질문 수
 * @param recallAtK 채점 대상 질문의 k별 recall 평균
 * @param hitRateAtK 채점 대상 질문의 k별 적중률
 * @param mrr 채점 대상 질문의 reciprocalRank 평균
 * @param zeroHitQuestionCount 기대 근거를 하나도 못 찾은 질문 수
 * @param falsePositiveAtK 채점 대상 질문의 k별 오검출 수 평균
 * @param excluded 집계에서 뺀 질문 수 (상호 배타, 판정 순서 고정)
 * @param scoredCountByType 유형별 채점 질문 수
 * @param recencyLatestHitRateAt5 RECENCY 유형에서 상위 5건에 최신 문서가 들어온 비율. 대상이 없으면 null
 * @param noEvidence "근거 없음" 유형 판정 결과
 */
public record AggregateMetrics(
    int totalCount,
    int scoredCount,
    Map<Integer, Double> recallAtK,
    Map<Integer, Double> hitRateAtK,
    double mrr,
    int zeroHitQuestionCount,
    Map<Integer, Double> falsePositiveAtK,
    Excluded excluded,
    Map<GoldenSetItemType, Integer> scoredCountByType,
    Double recencyLatestHitRateAt5,
    NoEvidence noEvidence
) {

    /**
     * 집계에서 뺀 질문 수. 판정 순서는 intentNotRag → fallbackPath → searchFailed → noEvidenceType이고
     * 한 질문은 한 버킷에만 들어간다.
     */
    public record Excluded(
        int intentNotRag,
        int fallbackPath,
        int searchFailed,
        int noEvidenceType
    ) {}

    /**
     * @param total NO_EVIDENCE 유형으로 판정된 질문 수
     * @param correctlyEmpty 후보 리스트가 비어 있어 정답인 질문 수
     * @param wronglyNonEmpty 후보가 남아 오답인 질문 수
     */
    public record NoEvidence(
        int total,
        int correctlyEmpty,
        int wronglyNonEmpty
    ) {}
}
