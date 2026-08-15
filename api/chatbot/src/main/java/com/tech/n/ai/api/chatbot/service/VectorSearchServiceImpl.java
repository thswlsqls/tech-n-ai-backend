package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.domain.mongodb.util.VectorSearchOptions;
import com.tech.n.ai.domain.mongodb.util.VectorSearchUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 벡터 검색 서비스 구현체
 *
 * MongoDB Atlas Vector Search를 사용하여 벡터 검색을 수행합니다.
 * Score Fusion 활성화 시 하이브리드 검색(벡터 + 최신성 직접 쿼리 + RRF 결합)을 수행합니다.
 *
 * 공식 문서: https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchServiceImpl implements VectorSearchService {

    private final EmbeddingModel embeddingModel;
    private final MongoTemplate mongoTemplate;

    @Override
    public SearchOutcome search(String query, Long userId, SearchOptions options) {
        // 1. 쿼리 임베딩 생성 (OpenAI text-embedding-3-small은 document/query 구분 없음)
        Embedding embedding = embeddingModel.embed(query).content();
        List<Float> queryVector = embedding.vectorAsList();

        // 2. Score Fusion 활성화 여부에 따라 분기
        SearchOutcome outcome;
        if (Boolean.TRUE.equals(options.enableScoreFusion())) {
            outcome = searchEmergingTechsHybrid(queryVector, options);
        } else {
            StandardSearchOutcome standard = searchEmergingTechs(queryVector, options);
            outcome = SearchOutcome.builder()
                .path(standard.failed() ? SearchPath.STANDARD_FAILED : SearchPath.STANDARD)
                .candidates(Collections.emptyList())
                .recencyQueryFailed(false)
                .results(standard.results())
                .build();
        }

        // 3. 유사도 점수로 정렬 및 최종 결과 수 제한
        List<SearchResult> results = outcome.results().stream()
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(options.maxResults())
            .collect(Collectors.toList());

        return SearchOutcome.builder()
            .path(outcome.path())
            .candidates(outcome.candidates())
            .recencyQueryFailed(outcome.recencyQueryFailed())
            .results(results)
            .build();
    }

    /**
     * 하이브리드 검색: Score Fusion 파이프라인 + 최신성 직접 쿼리 + RRF 결합
     */
    private SearchOutcome searchEmergingTechsHybrid(
            List<Float> queryVector, SearchOptions options) {
        try {
            // 소스 A: Score Fusion 벡터 검색
            List<SearchResult> vectorResults = searchWithScoreFusion(queryVector, options);
            log.info("Score Fusion vector search completed: {} results", vectorResults.size());

            // 소스 B: 최신성 직접 쿼리
            boolean isRecency = Boolean.TRUE.equals(options.recencyDetected());
            int recencyLimit = isRecency ? 5 : 3;
            List<SearchResult> recencyResults;
            boolean recencyQueryFailed = false;
            try {
                recencyResults = queryRecentDocuments(options, recencyLimit);
                log.info("Recency query completed: {} results", recencyResults.size());
            } catch (Exception e) {
                log.warn("Recency query failed, using vector results only: {}", e.getMessage());
                recencyResults = Collections.emptyList();
                recencyQueryFailed = true;
            }

            // RRF 결합
            int maxResults = options.maxResults() != null ? options.maxResults() : 5;
            List<SearchResult> combined = applyRRF(vectorResults, recencyResults, isRecency, maxResults);
            log.info("RRF combination completed: {} results (recencyDetected={})", combined.size(), isRecency);

            return SearchOutcome.builder()
                .path(SearchPath.HYBRID)
                .candidates(vectorResults)
                .recencyQueryFailed(recencyQueryFailed)
                .results(combined)
                .build();

        } catch (Exception e) {
            log.error("Hybrid search failed, falling back to standard search: {}",
                e.getMessage(), e);
            StandardSearchOutcome fallback = searchEmergingTechs(queryVector, options);
            return SearchOutcome.builder()
                .path(fallback.failed()
                    ? SearchPath.HYBRID_FALLBACK_FAILED
                    : SearchPath.HYBRID_FALLBACK_STANDARD)
                .candidates(Collections.emptyList())
                .recencyQueryFailed(false)
                .results(fallback.results())
                .build();
        }
    }

    /**
     * Score Fusion 벡터 검색 (파이프라인 내 recency + vector 결합)
     */
    private List<SearchResult> searchWithScoreFusion(
            List<Float> queryVector, SearchOptions options) {

        boolean isRecency = Boolean.TRUE.equals(options.recencyDetected());
        int searchLimit = (options.maxResults() != null ? options.maxResults() : 5) * 3;

        VectorSearchOptions vectorOptions = VectorSearchOptions.builder()
            .indexName(VectorSearchUtil.INDEX_EMERGING_TECHS)
            .numCandidates(isRecency ? 200 : 150)
            .limit(searchLimit)
            .minScore(options.minSimilarityScore() != null ? options.minSimilarityScore() : 0.7)
            .exact(Boolean.TRUE.equals(options.exact()))
            .filter(buildProviderFilter(options))
            .enableScoreFusion(true)
            .vectorWeight(isRecency ? 0.5 : 0.85)
            .recencyWeight(isRecency ? 0.5 : 0.15)
            .build();

        List<Document> pipeline =
            VectorSearchUtil.createEmergingTechSearchPipelineWithFusion(queryVector, vectorOptions);

        List<Document> results = mongoTemplate
            .getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS)
            .aggregate(pipeline)
            .into(new ArrayList<>());

        return results.stream()
            .map(doc -> convertToSearchResult(doc, "EMERGING_TECH"))
            .collect(Collectors.toList());
    }

    /**
     * 최신 문서 직접 쿼리 (published_at DESC)
     *
     * 다중 provider/updateType일 때는 각 차원별 개별 쿼리로 최신 문서를 보장한다.
     * 단일 $in 쿼리를 사용하면 특정 값이 최근에 집중된 경우 다른 값의 문서가 제외될 수 있다.
     *
     * 전략 매트릭스:
     * - provider(N>1) × updateType(M>1), N×M≤20: 교차 개별 쿼리
     * - provider(N>1) × updateType(M>1), N×M>20: $in fallback 쿼리
     * - provider(N>1) × updateType(0/1): provider별 개별 쿼리
     * - provider(0/1) × updateType(M>1): updateType별 개별 쿼리
     * - provider(0/1) × updateType(0/1): 단일 쿼리
     */
    private static final int MAX_CROSS_PRODUCT_COMBINATIONS = 20;
    private static final int MIN_RESULTS_PER_COMBINATION = 2;

    private List<SearchResult> queryRecentDocuments(SearchOptions options, int limit) {
        List<String> providers = options.providerFilters();
        List<String> updateTypes = options.updateTypeFilters();
        boolean multiProvider = providers != null && providers.size() > 1;
        boolean multiUpdateType = updateTypes != null && updateTypes.size() > 1;

        // 다중 provider + 다중 updateType: 교차 쿼리 또는 $in fallback
        if (multiProvider && multiUpdateType) {
            int totalCombinations = providers.size() * updateTypes.size();
            if (totalCombinations > MAX_CROSS_PRODUCT_COMBINATIONS) {
                log.warn("Cross-product combinations ({}) exceed max ({}), using $in fallback",
                    totalCombinations, MAX_CROSS_PRODUCT_COMBINATIONS);
                return queryRecentDocumentsSingle(options, null, null, limit);
            }
            int perCombinationLimit = Math.max(MIN_RESULTS_PER_COMBINATION,
                limit / totalCombinations);
            List<SearchResult> allResults = new ArrayList<>();
            for (String provider : providers) {
                for (String updateType : updateTypes) {
                    allResults.addAll(queryRecentDocumentsSingle(
                        options, provider, updateType, perCombinationLimit));
                }
            }
            log.info("Cross-product queries: {}×{} combinations, {} total results",
                providers.size(), updateTypes.size(), allResults.size());
            return allResults;
        }

        // 다중 provider only: provider별 개별 쿼리
        if (multiProvider) {
            String singleType = (updateTypes != null && updateTypes.size() == 1)
                ? updateTypes.get(0) : null;
            int perProviderLimit = Math.max(MIN_RESULTS_PER_COMBINATION,
                limit / providers.size());
            List<SearchResult> allResults = new ArrayList<>();
            for (String provider : providers) {
                allResults.addAll(queryRecentDocumentsSingle(
                    options, provider, singleType, perProviderLimit));
            }
            return allResults;
        }

        // 다중 updateType only: updateType별 개별 쿼리 (기존 로직)
        if (multiUpdateType) {
            String singleProvider = (providers != null && providers.size() == 1)
                ? providers.get(0) : null;
            int perTypeLimit = Math.max(MIN_RESULTS_PER_COMBINATION,
                limit / updateTypes.size());
            List<SearchResult> allResults = new ArrayList<>();
            for (String updateType : updateTypes) {
                allResults.addAll(queryRecentDocumentsSingle(
                    options, singleProvider, updateType, perTypeLimit));
            }
            return allResults;
        }

        // 단일 또는 필터 없음: 단일 쿼리
        String singleProvider = (providers != null && providers.size() == 1)
            ? providers.get(0) : null;
        String singleType = (updateTypes != null && updateTypes.size() == 1)
            ? updateTypes.get(0) : null;
        return queryRecentDocumentsSingle(options, singleProvider, singleType, limit);
    }

    /**
     * 단일 (provider, update_type) 조합에 대한 최신 문서 직접 쿼리
     *
     * @param options 검색 옵션
     * @param provider provider 필터 (null이면 options.providerFilters()로 $in 필터 적용)
     * @param updateType update_type 필터 (null이면 options.updateTypeFilters()로 $in 필터 적용)
     * @param limit 결과 수 제한
     */
    private List<SearchResult> queryRecentDocumentsSingle(
            SearchOptions options, String provider, String updateType, int limit) {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is("PUBLISHED"));

        if (provider != null && !provider.isBlank()) {
            query.addCriteria(Criteria.where("provider").is(provider));
        } else if (options.providerFilters() != null && !options.providerFilters().isEmpty()) {
            if (options.providerFilters().size() == 1) {
                query.addCriteria(Criteria.where("provider").is(options.providerFilters().get(0)));
            } else {
                query.addCriteria(Criteria.where("provider").in(options.providerFilters()));
            }
        }

        if (updateType != null) {
            query.addCriteria(Criteria.where("update_type").is(updateType));
        } else if (options.updateTypeFilters() != null && !options.updateTypeFilters().isEmpty()) {
            if (options.updateTypeFilters().size() == 1) {
                query.addCriteria(Criteria.where("update_type").is(options.updateTypeFilters().get(0)));
            } else {
                query.addCriteria(Criteria.where("update_type").in(options.updateTypeFilters()));
            }
        }

        if (options.sourceTypeFilters() != null && !options.sourceTypeFilters().isEmpty()) {
            if (options.sourceTypeFilters().size() == 1) {
                query.addCriteria(Criteria.where("source_type").is(options.sourceTypeFilters().get(0)));
            } else {
                query.addCriteria(Criteria.where("source_type").in(options.sourceTypeFilters()));
            }
        }

        if (options.dateFrom() != null) {
            query.addCriteria(Criteria.where("published_at").gte(options.dateFrom()));
        }

        query.with(Sort.by(Sort.Direction.DESC, "published_at"));
        query.limit(limit);

        List<Document> docs = mongoTemplate.find(query, Document.class,
            VectorSearchUtil.COLLECTION_EMERGING_TECHS);

        return docs.stream()
            .map(doc -> convertToSearchResult(doc, "EMERGING_TECH"))
            .collect(Collectors.toList());
    }

    /**
     * Reciprocal Rank Fusion (RRF) 알고리즘
     *
     * RRF_score(d) = Σ w_r × (1 / (k + rank_r(d)))
     * k = 60 (MongoDB 공식 기본값)
     *
     * 참고: https://www.mongodb.com/docs/manual/reference/operator/aggregation/rankfusion/
     * 참고: Cormack, G.V., Clarke, C.L.A., Buettcher, S. (2009). SIGIR '09
     */
    private List<SearchResult> applyRRF(
            List<SearchResult> vectorResults,
            List<SearchResult> recencyResults,
            boolean recencyDetected,
            int maxResults) {

        final int k = 60;
        double vectorWeight = 1.0;
        double recencyWeight = recencyDetected ? 1.5 : 1.0;

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, SearchResult> documentMap = new LinkedHashMap<>();

        // 벡터 검색 결과 RRF 점수 계산
        for (int rank = 0; rank < vectorResults.size(); rank++) {
            SearchResult result = vectorResults.get(rank);
            String docId = result.documentId();
            if (docId == null) continue;
            double score = vectorWeight * (1.0 / (k + rank + 1));
            rrfScores.merge(docId, score, Double::sum);
            documentMap.putIfAbsent(docId, result);
        }

        // 최신성 직접 쿼리 결과 RRF 점수 계산
        for (int rank = 0; rank < recencyResults.size(); rank++) {
            SearchResult result = recencyResults.get(rank);
            String docId = result.documentId();
            if (docId == null) continue;
            double score = recencyWeight * (1.0 / (k + rank + 1));
            rrfScores.merge(docId, score, Double::sum);
            documentMap.putIfAbsent(docId, result);
        }

        // RRF 점수 기준 정렬 및 반환
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(maxResults)
            .map(entry -> SearchResult.builder()
                .documentId(entry.getKey())
                .text(documentMap.get(entry.getKey()).text())
                .score(entry.getValue())
                .collectionType(documentMap.get(entry.getKey()).collectionType())
                .metadata(documentMap.get(entry.getKey()).metadata())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Emerging Tech 벡터 검색 (status: PUBLISHED 필터 적용)
     *
     * MongoDB Atlas Vector Search의 $vectorSearch aggregation stage를 사용합니다.
     * status: "PUBLISHED" pre-filter가 기본 적용됩니다.
     */
    private StandardSearchOutcome searchEmergingTechs(List<Float> queryVector, SearchOptions options) {
        try {
            // pre-filter 생성 (provider + 날짜)
            Document combinedFilter = buildProviderFilter(options);

            // 최신성 감지 시 더 많은 후보 확보 (re-ranking을 위해)
            boolean isRecency = Boolean.TRUE.equals(options.recencyDetected());
            int searchLimit = options.maxResults() != null ? options.maxResults() : 5;
            if (isRecency) {
                searchLimit = searchLimit * 3;  // recency: 3배 (15)
            } else {
                searchLimit = searchLimit * 2;  // 일반: 2배 (10)
            }

            VectorSearchOptions vectorOptions = VectorSearchOptions.builder()
                .indexName(VectorSearchUtil.INDEX_EMERGING_TECHS)
                .numCandidates(isRecency ? 150 : (options.numCandidates() != null ? options.numCandidates() : 100))
                .limit(searchLimit)
                .minScore(options.minSimilarityScore() != null ? options.minSimilarityScore() : 0.7)
                .exact(Boolean.TRUE.equals(options.exact()))
                .filter(combinedFilter)
                .build();

            List<Document> pipeline = VectorSearchUtil.createEmergingTechSearchPipeline(
                queryVector, vectorOptions);

            List<Document> results = mongoTemplate
                .getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS)
                .aggregate(pipeline)
                .into(new ArrayList<>());

            return new StandardSearchOutcome(results.stream()
                .map(doc -> convertToSearchResult(doc, "EMERGING_TECH"))
                .collect(Collectors.toList()), false);

        } catch (Exception e) {
            log.error("Vector search for emerging techs failed: {}", e.getMessage(), e);
            return new StandardSearchOutcome(new ArrayList<>(), true);
        }
    }

    /**
     * 일반 벡터 검색 결과와 예외 발생 여부
     */
    private record StandardSearchOutcome(List<SearchResult> results, boolean failed) {}

    /**
     * provider + update_type + 날짜 pre-filter 생성
     *
     * 주의: $vectorSearch의 pre-filter에서 사용하려면 Vector Search Index에
     * 해당 필드가 filter로 등록되어 있어야 합니다.
     * - provider: 등록됨 (VectorSearchIndexConfig)
     * - update_type: 등록됨 (VectorSearchIndexConfig)
     * - published_at: 등록됨 (VectorSearchIndexConfig)
     * - source_type: 미등록 → queryRecentDocuments() (소스 B)에서만 사용
     */
    private Document buildProviderFilter(SearchOptions options) {
        List<Document> filters = new ArrayList<>();

        if (options.providerFilters() != null && !options.providerFilters().isEmpty()) {
            if (options.providerFilters().size() == 1) {
                filters.add(new Document("provider", options.providerFilters().get(0)));
            } else {
                filters.add(new Document("provider",
                    new Document("$in", options.providerFilters())));
            }
            log.info("Applying provider pre-filter: {}", options.providerFilters());
        }

        if (options.updateTypeFilters() != null && !options.updateTypeFilters().isEmpty()) {
            if (options.updateTypeFilters().size() == 1) {
                filters.add(new Document("update_type", options.updateTypeFilters().get(0)));
            } else {
                filters.add(new Document("update_type",
                    new Document("$in", options.updateTypeFilters())));
            }
            log.info("Applying update_type pre-filter: {}", options.updateTypeFilters());
        }

        if (options.dateFrom() != null) {
            java.util.Date dateFromAsDate = java.sql.Timestamp.valueOf(options.dateFrom());
            filters.add(new Document("published_at",
                new Document("$gte", dateFromAsDate)));
            log.info("Applying date pre-filter: published_at >= {}", options.dateFrom());
        }

        if (filters.size() == 1) {
            return filters.get(0);
        } else if (filters.size() > 1) {
            return new Document("$and", filters);
        }
        return null;
    }

    /**
     * MongoDB Document를 SearchResult로 변환
     *
     * @param doc MongoDB Document
     * @param collectionType 컬렉션 타입 (BOOKMARK, EMERGING_TECH)
     * @return SearchResult
     */
    private SearchResult convertToSearchResult(Document doc, String collectionType) {
        // Score Fusion 파이프라인에서는 combinedScore가 있고, 기존에서는 score가 있음
        Double score = doc.getDouble("combinedScore");
        if (score == null) {
            score = doc.getDouble("score");
        }
        if (score == null) {
            score = 0.0;
        }

        return SearchResult.builder()
            .documentId(doc.getObjectId("_id") != null ? doc.getObjectId("_id").toString() : null)
            .text(doc.getString("embedding_text"))
            .score(score)
            .collectionType(collectionType)
            .metadata(doc)
            .build();
    }
}
