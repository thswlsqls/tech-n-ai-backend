package com.tech.n.ai.domain.mongodb.service.dto;

import java.util.List;

/**
 * 질문에서 만든 후보 키로 그래프에서 찾아낸 노드 하나
 *
 * @param key 노드 키 (예: Company|openai)
 * @param type 노드 타입 라벨
 * @param name 노드에 저장된 원본 표기
 * @param externalIds 이 노드가 나온 emerging_techs 문서들의 external_id
 * @param hop 0이면 질문이 직접 맞춘 시드, 1이면 엣지를 한 번 타고 간 이웃
 */
public record GraphNodeMatch(
    String key,
    String type,
    String name,
    List<String> externalIds,
    int hop
) {}
