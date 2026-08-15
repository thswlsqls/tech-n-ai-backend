package com.tech.n.ai.batch.eval.goldenset;

import java.util.List;

/**
 * 골든셋 질문 한 건
 *
 * @param id 질문 식별자
 * @param question 사용자가 실제로 입력할 법한 질문 원문
 * @param type 질문 유형
 * @param expectedExternalIds 정답으로 인정하는 문서의 external_id 목록. NO_EVIDENCE는 비어 있다
 * @param latestExternalId RECENCY 유형에서 "가장 최신"으로 봐야 할 문서. 그 외에는 null
 * @param note 사람이 검수할 때 참고할 메모
 */
public record GoldenSetItem(
    String id,
    String question,
    GoldenSetItemType type,
    List<String> expectedExternalIds,
    String latestExternalId,
    String note
) {}
