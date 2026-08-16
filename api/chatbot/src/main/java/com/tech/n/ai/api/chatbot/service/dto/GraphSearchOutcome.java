package com.tech.n.ai.api.chatbot.service.dto;

import java.util.List;

/**
 * 그래프 검색 한 번의 결과 전체
 *
 * 값이 없을 때도 리스트는 빈 리스트다. null은 넣지 않는다.
 *
 * @param enabled 그래프 경로가 켜져 있었는지 여부
 * @param results 문서 순위대로 만든 검색 결과
 * @param seedKeys 질문이 직접 맞춘 노드 키(0홉)
 * @param expandedKeys 엣지를 타고 간 이웃 노드 키(1홉)
 * @param externalIds 순위대로 정렬한 문서 external_id
 * @param capped 결과 개수 상한에 걸려 뒤가 잘렸는지 여부
 * @param latencyMs 그래프 조회에 걸린 시간
 */
public record GraphSearchOutcome(
    boolean enabled,
    List<SearchResult> results,
    List<String> seedKeys,
    List<String> expandedKeys,
    List<String> externalIds,
    boolean capped,
    long latencyMs
) {

    /** 그래프 경로가 꺼져 있어 조회 자체를 하지 않은 경우 */
    public static GraphSearchOutcome disabled() {
        return new GraphSearchOutcome(false, List.of(), List.of(), List.of(), List.of(), false, 0L);
    }

    /** 조회는 했지만 걸린 노드나 문서가 없는 경우 */
    public static GraphSearchOutcome empty(long latencyMs) {
        return new GraphSearchOutcome(true, List.of(), List.of(), List.of(), List.of(), false, latencyMs);
    }
}
