package com.tech.n.ai.api.chatbot.service.dto;

import lombok.Builder;

import java.util.List;

/**
 * 벡터 검색 한 번의 결과 전체
 *
 * 운영 코드는 {@code results()}만 쓰고, 평가 잡은 경로와 후보 리스트까지 함께 본다.
 *
 * @param path 검색이 지나간 경로
 * @param candidates RRF 결합 직전의 벡터 검색 후보(최대 15건). 하이브리드 경로가 아니면 빈 리스트
 * @param recencyQueryFailed 최신성 직접 쿼리가 예외로 끝났는지 여부
 * @param results 점수 내림차순 정렬과 maxResults 절단까지 끝낸 최종 결과
 */
@Builder
public record SearchOutcome(
    SearchPath path,
    List<SearchResult> candidates,
    boolean recencyQueryFailed,
    List<SearchResult> results
) {}
