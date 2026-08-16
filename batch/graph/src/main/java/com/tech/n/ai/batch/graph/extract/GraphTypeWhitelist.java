package com.tech.n.ai.batch.graph.extract;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 추출 결과를 정해둔 타입 목록으로 거른다.
 *
 * LLMGraphTransformer에 allowedNodes·allowedRelationships를 넘겨도 출력이 그 목록 안에 있다는
 * 보장은 없다. 프롬프트로 부탁할 뿐이고, 응답을 읽을 때 타입이 목록 밖이어도 그대로 통과시키며
 * 타입이 아예 없으면 "Node"로 채워 넣는다. 목록 안의 타입만 저장된다는 성질은 여기서 만든다.
 */
public final class GraphTypeWhitelist {

    private GraphTypeWhitelist() {
    }

    public static ExtractedGraph filter(GraphDocument graphDocument) {
        Map<String, Integer> rejectedNodeTypes = new HashMap<>();
        Map<String, Integer> rejectedRelationTypes = new HashMap<>();

        // 엣지에서 양 끝 노드가 통과했는지 확인하려면 노드 판정 결과를 들고 있어야 한다
        Map<GraphNode, GraphNodeType> acceptedNodeTypes = new HashMap<>();
        List<ExtractedGraph.Node> nodes = new ArrayList<>();

        for (GraphNode node : graphDocument.nodes()) {
            Optional<GraphNodeType> type = GraphNodeType.fromLabel(node.type());
            if (type.isEmpty()) {
                count(rejectedNodeTypes, node.type());
                continue;
            }
            acceptedNodeTypes.put(node, type.get());
            nodes.add(new ExtractedGraph.Node(type.get(), node.id()));
        }

        List<ExtractedGraph.Edge> edges = new ArrayList<>();
        for (GraphEdge edge : graphDocument.relationships()) {
            Optional<GraphRelationType> type = GraphRelationType.fromLabel(edge.type());
            GraphNodeType sourceType = acceptedNodeTypes.get(edge.sourceNode());
            GraphNodeType targetType = acceptedNodeTypes.get(edge.targetNode());

            // 관계 타입이 목록 밖이거나, 양 끝 노드 중 하나라도 버려졌으면 엣지도 버린다
            if (type.isEmpty() || sourceType == null || targetType == null) {
                count(rejectedRelationTypes, edge.type());
                continue;
            }
            edges.add(new ExtractedGraph.Edge(
                sourceType, edge.sourceNode().id(),
                type.get(),
                targetType, edge.targetNode().id()));
        }

        return new ExtractedGraph(nodes, edges, rejectedNodeTypes, rejectedRelationTypes);
    }

    private static void count(Map<String, Integer> counts, String typeName) {
        counts.merge(typeName == null ? "(없음)" : typeName, 1, Integer::sum);
    }
}
