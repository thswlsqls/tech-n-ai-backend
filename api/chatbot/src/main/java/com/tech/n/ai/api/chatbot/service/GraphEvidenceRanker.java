package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.domain.mongodb.service.dto.GraphNodeMatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 그래프에서 찾은 노드들을 문서 단위로 접어 순위를 매긴다.
 *
 * 한 문서를 여러 노드가 가리켜도 결과에는 한 번만 들어간다. 대신 몇 개의 노드가 가리켰는지를
 * 세서 순위에 쓴다. 질문이 짚은 대상 여러 개가 함께 걸린 문서가 진짜 답일 가능성이 높다.
 */
public final class GraphEvidenceRanker {

    private GraphEvidenceRanker() {
    }

    /**
     * @param externalIds 순위대로 정렬한 문서 external_id
     * @param capped 상한에 걸려 뒤가 잘렸는지 여부
     */
    public record Ranked(List<String> externalIds, boolean capped) {}

    public static Ranked rank(List<GraphNodeMatch> matches, int limit) {
        if (matches == null || matches.isEmpty() || limit <= 0) {
            return new Ranked(List.of(), false);
        }

        Map<String, Evidence> byExternalId = new LinkedHashMap<>();
        Set<String> countedNodeKeys = new LinkedHashSet<>();

        for (GraphNodeMatch match : matches) {
            if (match.key() != null && !countedNodeKeys.add(match.key())) {
                continue;
            }
            int nodeSize = match.externalIds().size();
            for (String externalId : new LinkedHashSet<>(match.externalIds())) {
                byExternalId
                    .computeIfAbsent(externalId, id -> new Evidence())
                    .add(nodeSize, match.hop());
            }
        }

        List<String> ordered = new ArrayList<>(byExternalId.keySet());
        ordered.sort(Comparator
            .comparingInt((String id) -> byExternalId.get(id).nodeCount).reversed()
            .thenComparingInt(id -> byExternalId.get(id).smallestNodeSize)
            .thenComparingInt(id -> byExternalId.get(id).closestHop)
            .thenComparing(Comparator.naturalOrder()));

        boolean capped = ordered.size() > limit;
        return new Ranked(List.copyOf(ordered.subList(0, Math.min(limit, ordered.size()))), capped);
    }

    /**
     * 한 문서에 대해 모인 근거. 어떤 노드들이 가리켰는지를 순위 판단에 쓸 만큼만 요약해 둔다.
     */
    private static final class Evidence {

        private int nodeCount;
        private int smallestNodeSize = Integer.MAX_VALUE;
        private int closestHop = Integer.MAX_VALUE;

        private void add(int nodeSize, int hop) {
            nodeCount++;
            smallestNodeSize = Math.min(smallestNodeSize, nodeSize);
            closestHop = Math.min(closestHop, hop);
        }
    }
}
