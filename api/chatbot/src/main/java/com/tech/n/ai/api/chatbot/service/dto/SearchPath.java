package com.tech.n.ai.api.chatbot.service.dto;

/**
 * 검색이 실제로 지나간 경로
 *
 * 평가 잡이 fallback으로 끝난 질문을 집계에서 빼려면 어느 경로로 갔는지 알아야 한다.
 * "결과 없음"은 경로가 아니라 후보 리스트가 비었는지로 판단한다.
 */
public enum SearchPath {

    /** 하이브리드 검색(Score Fusion + RRF) 성공 */
    HYBRID,

    /** 하이브리드 검색이 예외로 끝나 일반 벡터 검색으로 넘어갔고, 그 검색은 성공 */
    HYBRID_FALLBACK_STANDARD,

    /** 하이브리드 검색이 예외로 끝난 뒤 fallback 일반 벡터 검색도 예외 */
    HYBRID_FALLBACK_FAILED,

    /** Score Fusion 비활성 상태로 일반 벡터 검색 성공 */
    STANDARD,

    /** Score Fusion 비활성 상태로 실행한 일반 벡터 검색이 예외 */
    STANDARD_FAILED
}
