package com.tech.n.ai.batch.graph.extract;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;

import java.util.List;
import java.util.Map;

/**
 * 화이트리스트를 통과한 추출 결과
 *
 * 버린 쪽도 함께 들고 있는다. 어떤 타입이 몇 건 나왔는지 리포트에 남겨야 다음 라운드에
 * 타입 목록을 손볼지 판단할 수 있기 때문이다.
 */
public record ExtractedGraph(
    List<Node> nodes,
    List<Edge> edges,
    Map<String, Integer> rejectedNodeTypes,
    Map<String, Integer> rejectedRelationTypes
) {

    /** name은 모델이 준 원본 표기다. 키를 만들 때 정규화한다 */
    public record Node(GraphNodeType type, String name) {}

    /** 양 끝 노드는 둘 다 화이트리스트를 통과한 것만 들어온다 */
    public record Edge(
        GraphNodeType sourceType,
        String sourceName,
        GraphRelationType type,
        GraphNodeType targetType,
        String targetName
    ) {}
}
