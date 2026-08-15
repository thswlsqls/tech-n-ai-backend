package com.tech.n.ai.batch.eval.scoring;

import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;

import java.util.List;
import java.util.Set;

/**
 * 집계에 넘기는 질문 한 건의 실행 결과
 *
 * @param id 질문 식별자
 * @param type 질문 유형
 * @param intentRagRequired 의도 분류가 RAG_REQUIRED로 나왔는지
 * @param fallbackPath 검색이 fallback 경로로 끝났는지
 * @param searchFailed 검색이 예외로 끝났는지
 * @param candidatesEmpty RRF 직전 후보가 비었는지 ("근거 없음" 판정 신호)
 * @param rankedExternalIds 채점 기준으로 고른 순위 목록
 * @param expectedExternalIds 기대 근거 집합
 * @param latestExternalId RECENCY 유형에서 최신으로 봐야 할 문서. 그 외에는 null
 */
public record QuestionOutcome(
    String id,
    GoldenSetItemType type,
    boolean intentRagRequired,
    boolean fallbackPath,
    boolean searchFailed,
    boolean candidatesEmpty,
    List<String> rankedExternalIds,
    Set<String> expectedExternalIds,
    String latestExternalId
) {}
