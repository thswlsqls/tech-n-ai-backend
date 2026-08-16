package com.tech.n.ai.api.chatbot.service.dto;

import java.util.List;

/**
 * 검색 한 번의 결과 전체
 *
 * 운영 코드는 {@code merged()}만 쓰고, 평가 잡은 벡터·그래프 결과와 지연까지 따로 본다.
 *
 * @param vector 벡터 검색 결과 묶음
 * @param graph 그래프 검색 결과 묶음. 그래프를 끄면 {@code enabled=false}인 빈 결과다
 * @param merged 벡터 결과 뒤에 그래프 결과를 붙이고 중복을 뺀 목록
 * @param path 근거를 어디서 얻었는지
 * @param vectorLatencyMs 벡터 검색에 걸린 시간
 * @param graphLatencyMs 그래프 검색에 걸린 시간. 그래프를 끄면 0이다
 */
public record RetrievalOutcome(
    SearchOutcome vector,
    GraphSearchOutcome graph,
    List<SearchResult> merged,
    RetrievalPath path,
    long vectorLatencyMs,
    long graphLatencyMs
) {}
