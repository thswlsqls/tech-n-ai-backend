package com.tech.n.ai.batch.eval.scoring;

/**
 * 리포트에 싣는 후보 한 건
 *
 * @param externalId 문서의 external_id. 뽑지 못하면 null
 * @param documentId MongoDB _id 문자열
 * @param fusionRank Score Fusion 파이프라인이 내려준 순위(1-based)
 * @param vectorRank vectorScore 내림차순으로 다시 매긴 순위(1-based)
 * @param vectorScore 파이프라인이 계산한 벡터 유사도 점수
 * @param combinedScore 벡터 점수와 최신성 점수를 합친 점수
 * @param expected 기대 근거에 들어 있는 문서인지 여부
 */
public record RankedCandidate(
    String externalId,
    String documentId,
    int fusionRank,
    int vectorRank,
    Double vectorScore,
    Double combinedScore,
    boolean expected
) {}
