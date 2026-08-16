package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 벡터 검색과 그래프 검색을 돌리고 두 결과를 합친다.
 *
 * 운영 챗봇과 평가 잡이 같은 코드를 타도록 검색 단계를 여기 하나로 모았다.
 * 벡터 검색 구현과 그래프 검색 구현은 그대로 두고 호출과 병합만 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    /** 벡터 결과가 하나도 없을 때 그래프 점수를 매길 기준값 */
    private static final double EMPTY_VECTOR_FLOOR_SCORE = 0.01;

    private final VectorSearchService vectorSearchService;
    private final GraphSearchService graphSearchService;

    @Value("${chatbot.rag.graph.enabled:false}")
    private boolean graphEnabled;

    public RetrievalOutcome retrieve(String query, Long userId, SearchOptions options) {
        long vectorStart = System.currentTimeMillis();
        SearchOutcome vector = vectorSearchService.search(query, userId, options);
        long vectorLatencyMs = System.currentTimeMillis() - vectorStart;

        GraphSearchOutcome graph = graphEnabled ? searchGraph(query) : GraphSearchOutcome.disabled();

        List<SearchResult> merged = merge(vector, graph);
        int graphAddedCount = merged.size() - vector.results().size();

        return new RetrievalOutcome(
            vector,
            graph,
            merged,
            path(!vector.results().isEmpty(), graphAddedCount > 0),
            vectorLatencyMs,
            graph.latencyMs());
    }

    /**
     * 그래프가 죽어도 벡터 결과만으로 답은 만들 수 있어야 한다. 예외를 잡고 빈 결과로 계속 간다.
     */
    private GraphSearchOutcome searchGraph(String query) {
        long startedAt = System.currentTimeMillis();
        try {
            return graphSearchService.search(query);
        } catch (Exception e) {
            log.warn("Graph search failed, continuing with vector results only: {}", e.getMessage(), e);
            return GraphSearchOutcome.empty(System.currentTimeMillis() - startedAt);
        }
    }

    /**
     * 벡터 결과 뒤에 그래프 결과를 붙인다.
     *
     * 벡터가 이미 물고 온 문서는 뺀다. 남은 그래프 문서 점수는 벡터 최저점을 순위+1로 나눠 다시 매긴다.
     * 순위가 1부터 시작하니 어떤 그래프 문서도 벡터 최저점을 넘지 못하고, 뒤로 갈수록 낮아진다.
     * 그래프가 벡터 상위 자리를 밀어내지 않게 하려는 것이다.
     */
    List<SearchResult> merge(SearchOutcome vector, GraphSearchOutcome graph) {
        List<SearchResult> vectorResults = vector.results();
        List<SearchResult> merged = new ArrayList<>(vectorResults);

        Set<String> seenDocumentIds = new HashSet<>();
        for (SearchResult result : vectorResults) {
            if (result.documentId() != null) {
                seenDocumentIds.add(result.documentId());
            }
        }

        double floorScore = floorScore(vectorResults);
        int graphRank = 1;
        for (SearchResult result : graph.results()) {
            if (result.documentId() != null && !seenDocumentIds.add(result.documentId())) {
                continue;
            }
            merged.add(rescored(result, floorScore / (graphRank + 1)));
            graphRank++;
        }
        return List.copyOf(merged);
    }

    private double floorScore(List<SearchResult> vectorResults) {
        double floor = Double.MAX_VALUE;
        for (SearchResult result : vectorResults) {
            if (result.score() != null && result.score() < floor) {
                floor = result.score();
            }
        }
        return floor == Double.MAX_VALUE ? EMPTY_VECTOR_FLOOR_SCORE : floor;
    }

    private SearchResult rescored(SearchResult result, double score) {
        return SearchResult.builder()
            .documentId(result.documentId())
            .text(result.text())
            .score(score)
            .collectionType(result.collectionType())
            .metadata(result.metadata())
            .build();
    }

    private RetrievalPath path(boolean hasVector, boolean hasGraph) {
        if (hasVector && hasGraph) {
            return RetrievalPath.BOTH;
        }
        if (hasVector) {
            return RetrievalPath.VECTOR_ONLY;
        }
        if (hasGraph) {
            return RetrievalPath.GRAPH_ONLY;
        }
        return RetrievalPath.NONE;
    }
}
