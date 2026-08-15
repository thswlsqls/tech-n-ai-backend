package com.tech.n.ai.batch.eval.goldenset;

/**
 * 골든셋 질문 유형
 */
public enum GoldenSetItemType {

    /** 문서 하나로 답이 정해지는 단일 사실 질문 */
    SINGLE_FACT,

    /** 특정 제공자로 범위를 좁힌 질문 */
    PROVIDER_SCOPED,

    /** 특정 업데이트 종류로 범위를 좁힌 질문 */
    TYPE_SCOPED,

    /** 최신성 키워드가 들어간 질문 */
    RECENCY,

    /** 답하려면 문서 여러 건을 엮어야 하는 질문 */
    MULTI_HOP,

    /** 컬렉션에 근거 문서가 없어야 정답인 질문 */
    NO_EVIDENCE
}
