package com.tech.n.ai.api.chatbot.service.dto;

/**
 * 질문 하나가 근거를 어디서 얻었는지
 *
 * 합친 목록에 실제로 들어간 결과만 센다. 그래프를 돌렸어도 중복 제거로 다 빠졌으면 그래프는 없는 것이다.
 */
public enum RetrievalPath {

    /** 벡터 결과만 있다 */
    VECTOR_ONLY,

    /** 그래프 결과만 있다 */
    GRAPH_ONLY,

    /** 두 쪽 다 있다 */
    BOTH,

    /** 양쪽 다 빈손이다 */
    NONE
}
