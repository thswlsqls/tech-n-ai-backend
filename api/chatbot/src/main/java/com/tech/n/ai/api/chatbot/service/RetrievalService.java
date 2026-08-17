package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.AugmentOutcome;
import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
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

    /** vectorScore를 아는 후보가 하나도 없을 때 쓰는 값. 어떤 문턱보다도 낮아 약함 판정에 걸린다 */
    private static final double UNKNOWN_VECTOR_SCORE = -1.0;

    /** SearchOptions에 maxResults가 없을 때 벡터 검색이 쓰는 기본값과 같은 값 */
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final VectorSearchService vectorSearchService;
    private final GraphSearchService graphSearchService;

    @Value("${chatbot.rag.augment.enabled:false}")
    private boolean augmentEnabled;

    @Value("${chatbot.rag.augment.max-attempts:2}")
    private int augmentMaxAttempts;

    @Value("${chatbot.rag.augment.min-vector-score:0.72}")
    private double augmentMinVectorScore;

    @Value("${chatbot.rag.augment.relaxed-min-score:0.5}")
    private double augmentRelaxedMinScore;

    public RetrievalOutcome retrieve(String query, Long userId, SearchOptions options) {
        long vectorStart = System.currentTimeMillis();
        SearchOutcome first = vectorSearchService.search(query, userId, options);

        SearchOutcome vector = first;
        int attempts = 0;
        boolean triggered = augmentEnabled && isWeak(first);
        if (triggered) {
            for (SearchOptions relaxed : relaxationLadder(options)) {
                if (attempts >= augmentMaxAttempts) {
                    break;
                }
                SearchOutcome retry = vectorSearchService.search(query, userId, relaxed);
                attempts++;
                vector = mergeOutcomes(vector, retry, options);
                if (!isWeak(vector)) {
                    break;
                }
            }
        }
        // 재검색까지 감싼 시간이다. 보강을 켠 실행의 검색 지연에는 재검색 시간이 들어간다.
        long vectorLatencyMs = System.currentTimeMillis() - vectorStart;

        GraphSearchOutcome graph = searchGraph(query);

        List<SearchResult> merged = merge(vector, graph);
        int graphAddedCount = merged.size() - vector.results().size();

        return new RetrievalOutcome(
            vector,
            graph,
            merged,
            path(!vector.results().isEmpty(), graphAddedCount > 0),
            vectorLatencyMs,
            graph.latencyMs(),
            new AugmentOutcome(triggered, attempts, vector.results().size() > first.results().size()));
    }

    /**
     * 근거가 약한지 본다. 후보가 하나도 없거나, 후보 최고 vectorScore가 문턱에 못 미치면 약하다.
     */
    private boolean isWeak(SearchOutcome outcome) {
        return outcome.candidates().isEmpty() || maxVectorScore(outcome) < augmentMinVectorScore;
    }

    /**
     * 후보 중 vectorScore 최고값. 점수를 아는 후보가 하나도 없으면 -1.0이다.
     *
     * 후보의 {@code score()}는 벡터 점수에 최신성 점수를 섞은 combinedScore라
     * 질문과 얼마나 비슷한 문서인지를 그대로 보여주지 못한다.
     * 원본 벡터 점수는 metadata에 따로 남아 있으므로 거기서 꺼낸다.
     */
    private double maxVectorScore(SearchOutcome outcome) {
        double max = UNKNOWN_VECTOR_SCORE;
        for (SearchResult candidate : outcome.candidates()) {
            Double score = vectorScore(candidate);
            if (score != null && score > max) {
                max = score;
            }
        }
        return max;
    }

    private Double vectorScore(SearchResult result) {
        if (result.metadata() instanceof Document doc) {
            return doc.getDouble("vectorScore");
        }
        return null;
    }

    /**
     * 1차 검색 결과 뒤에 재검색이 새로 물어온 것만 붙인다. 1차 결과를 갈아치우지 않는다.
     *
     * 필터를 풀면 최신성 직접 쿼리까지 같이 바뀌고 후보·결과 개수 상한도 완화 뒤에 다시 걸린다.
     * 그래서 재검색 결과는 1차의 상위집합이 아니라 다른 집합이고, 통째로 바꾸면 이미 찾아둔 정답을 잃는다.
     */
    private SearchOutcome mergeOutcomes(SearchOutcome base, SearchOutcome retry, SearchOptions options) {
        return SearchOutcome.builder()
            .path(base.path())
            .candidates(mergedCandidates(base.candidates(), retry.candidates()))
            .recencyQueryFailed(base.recencyQueryFailed() || retry.recencyQueryFailed())
            .results(mergedResults(base.results(), retry.results(), maxResults(options)))
            .build();
    }

    /**
     * 1차 후보 뒤에 재검색 후보 중 처음 보는 문서만 붙이고, 두 목록 중 긴 쪽 길이로 자른다.
     *
     * 검색 한 번이 돌려주는 최대 길이를 넘지 않게 하려는 것이다. 후보 목록 길이가 늘어나면
     * 이 목록을 k로 쓰는 순위 지표를 합치기 전 실행과 같은 기준으로 볼 수 없다.
     */
    private List<SearchResult> mergedCandidates(List<SearchResult> base, List<SearchResult> retry) {
        List<SearchResult> merged = new ArrayList<>(base);
        Set<String> seenDocumentIds = documentIds(base);
        int limit = Math.max(base.size(), retry.size());
        for (SearchResult candidate : retry) {
            if (merged.size() >= limit) {
                break;
            }
            if (candidate.documentId() != null && !seenDocumentIds.add(candidate.documentId())) {
                continue;
            }
            merged.add(candidate);
        }
        return List.copyOf(merged);
    }

    /**
     * 1차 결과 뒤에 재검색 결과 중 처음 보는 문서만 붙이고, maxResults까지만 남긴다.
     *
     * 붙이는 문서 점수는 1차 최저점 아래로 다시 매긴다. 그래프 결과를 붙일 때와 같은 규칙이다.
     * 뒤에서 {@code ResultRefinementChain.refine()}이 점수 내림차순으로 다시 정렬하고 자르기 때문에,
     * 점수를 그대로 두면 재검색 문서가 1차 문서 앞으로 끼어들어 앞자리 보존이 깨진다.
     */
    private List<SearchResult> mergedResults(List<SearchResult> base, List<SearchResult> retry, int maxResults) {
        List<SearchResult> merged = new ArrayList<>(base);
        Set<String> seenDocumentIds = documentIds(base);
        double floorScore = floorScore(base);
        int retryRank = 1;
        for (SearchResult result : retry) {
            if (merged.size() >= maxResults) {
                break;
            }
            if (result.documentId() != null && !seenDocumentIds.add(result.documentId())) {
                continue;
            }
            merged.add(rescored(result, floorScore / (retryRank + 1)));
            retryRank++;
        }
        return List.copyOf(merged);
    }

    private int maxResults(SearchOptions options) {
        return options.maxResults() != null ? options.maxResults() : DEFAULT_MAX_RESULTS;
    }

    private Set<String> documentIds(List<SearchResult> results) {
        Set<String> ids = new HashSet<>();
        for (SearchResult result : results) {
            if (result.documentId() != null) {
                ids.add(result.documentId());
            }
        }
        return ids;
    }

    /**
     * 조건을 단계적으로 푼 검색 옵션 목록. 질의 문자열은 건드리지 않는다.
     *
     * 1단계는 provider·updateType 필터를 떼고, 2단계는 거기에 유사도 문턱까지 낮춘다.
     * 앞 단계와 실질적으로 같아지는 단계는 빼는데, 같은 검색을 다시 돌려봐야 결과는 그대로이고
     * 임베딩 호출만 한 번 더 나가기 때문이다.
     */
    private List<SearchOptions> relaxationLadder(SearchOptions options) {
        List<SearchOptions> ladder = new ArrayList<>();
        if (hasCategoryFilters(options)) {
            ladder.add(relaxed(options, options.minSimilarityScore()));
        }
        Double minScore = options.minSimilarityScore();
        if (minScore == null || minScore > augmentRelaxedMinScore) {
            ladder.add(relaxed(options, augmentRelaxedMinScore));
        }
        return List.copyOf(ladder);
    }

    private boolean hasCategoryFilters(SearchOptions options) {
        return (options.providerFilters() != null && !options.providerFilters().isEmpty())
            || (options.updateTypeFilters() != null && !options.updateTypeFilters().isEmpty());
    }

    /**
     * provider·updateType 필터를 뗀 옵션. 나머지 조건은 1차 검색 그대로 둔다.
     */
    private SearchOptions relaxed(SearchOptions options, Double minSimilarityScore) {
        return SearchOptions.builder()
            .includeEmergingTechs(options.includeEmergingTechs())
            .maxResults(options.maxResults())
            .numCandidates(options.numCandidates())
            .minSimilarityScore(minSimilarityScore)
            .exact(options.exact())
            .providerFilters(null)
            .recencyDetected(options.recencyDetected())
            .dateFrom(options.dateFrom())
            .enableScoreFusion(options.enableScoreFusion())
            .updateTypeFilters(null)
            .sourceTypeFilters(options.sourceTypeFilters())
            .build();
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
    private List<SearchResult> merge(SearchOutcome vector, GraphSearchOutcome graph) {
        List<SearchResult> vectorResults = vector.results();
        List<SearchResult> merged = new ArrayList<>(vectorResults);

        Set<String> seenDocumentIds = documentIds(vectorResults);

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
