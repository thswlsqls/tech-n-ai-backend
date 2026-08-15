package com.tech.n.ai.batch.eval.scoring;

import java.util.Map;

/**
 * 질문 한 건의 검색 품질 지표
 *
 * @param recallAtK k별 recall (상위 k건에 들어온 기대 근거 수 ÷ 기대 근거 수)
 * @param hitAtK k별 적중 여부 (상위 k건에 기대 근거가 하나라도 있으면 true)
 * @param reciprocalRank 첫 적중 순위의 역수. 적중이 없으면 0
 * @param firstHitRank 첫 적중 순위(1-based). 적중이 없으면 null
 * @param falsePositiveAtK k별 오검출 수 (상위 k건 중 기대 근거가 아닌 문서 수)
 */
public record RetrievalMetrics(
    Map<Integer, Double> recallAtK,
    Map<Integer, Boolean> hitAtK,
    double reciprocalRank,
    Integer firstHitRank,
    Map<Integer, Integer> falsePositiveAtK
) {}
