package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.domain.mongodb.service.TechGraphReader;
import com.tech.n.ai.domain.mongodb.service.dto.GraphNodeMatch;
import com.tech.n.ai.domain.mongodb.util.VectorSearchUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 질문에서 그래프를 타고 근거 문서를 찾는다.
 *
 * 질문 → 노드 키 후보 → 그래프 조회 → 문서 순위 → emerging_techs 원본 조회 순서로 간다.
 * 벡터 검색과는 별개로 돌고, 두 결과를 합치는 일은 이 클래스가 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSearchService {

    private static final String COLLECTION = VectorSearchUtil.COLLECTION_EMERGING_TECHS;
    private static final String COLLECTION_TYPE = "EMERGING_TECH";

    private final TechGraphReader techGraphReader;
    private final MongoTemplate mongoTemplate;

    @Value("${chatbot.rag.graph.enabled:false}")
    private boolean graphEnabled;

    @Value("${chatbot.rag.graph.max-results:10}")
    private int maxResults;

    @Value("${chatbot.rag.graph.max-seeds:20}")
    private int maxSeeds;

    @Value("${chatbot.rag.graph.max-edges-per-seed:20}")
    private int maxEdgesPerSeed;

    @Value("${chatbot.rag.graph.max-time-ms:2000}")
    private long maxTimeMs;

    public GraphSearchOutcome search(String question) {
        if (!graphEnabled) {
            return GraphSearchOutcome.disabled();
        }

        long startedAt = System.currentTimeMillis();

        GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question);
        List<GraphNodeMatch> matches = techGraphReader.findMatches(
            seeds.candidateKeys(), seeds.nameLiterals(), maxSeeds, maxEdgesPerSeed, maxTimeMs);
        if (matches.isEmpty()) {
            return GraphSearchOutcome.empty(System.currentTimeMillis() - startedAt);
        }

        GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(matches, maxResults);
        if (ranked.externalIds().isEmpty()) {
            return GraphSearchOutcome.empty(System.currentTimeMillis() - startedAt);
        }

        List<SearchResult> results = loadDocuments(ranked.externalIds());
        long latencyMs = System.currentTimeMillis() - startedAt;

        log.info("Graph search completed: {} seeds, {} documents, {} ms",
            matches.size(), results.size(), latencyMs);

        return new GraphSearchOutcome(
            true,
            results,
            keysAtHop(matches, 0),
            keysAtHop(matches, 1),
            ranked.externalIds(),
            ranked.capped(),
            latencyMs);
    }

    /**
     * external_id 목록으로 원본 문서를 한 번에 읽어 SearchResult로 바꾼다.
     * find는 순서를 지켜주지 않으므로 순위 목록 순서대로 다시 세운다.
     */
    private List<SearchResult> loadDocuments(List<String> externalIds) {
        Query query = Query.query(
                Criteria.where("external_id").in(externalIds).and("status").is("PUBLISHED"))
            .limit(externalIds.size());
        query.maxTimeMsec(maxTimeMs);

        List<Document> documents = mongoTemplate.find(query, Document.class, COLLECTION);

        Map<String, Document> byExternalId = new LinkedHashMap<>();
        for (Document document : documents) {
            byExternalId.putIfAbsent(document.getString("external_id"), document);
        }

        List<SearchResult> results = new ArrayList<>();
        for (String externalId : externalIds) {
            Document document = byExternalId.get(externalId);
            if (document == null) {
                continue;
            }
            results.add(toSearchResult(document, results.size()));
        }
        return List.copyOf(results);
    }

    /**
     * 점수는 순위 역수로 임시로 매긴다. 벡터 결과와 합칠 때 다시 매기므로 이 값 자체에는 뜻이 없다.
     */
    private SearchResult toSearchResult(Document document, int rank) {
        return SearchResult.builder()
            .documentId(document.getObjectId("_id") != null
                ? document.getObjectId("_id").toString() : null)
            .text(document.getString("embedding_text"))
            .score(1.0 / (rank + 1))
            .collectionType(COLLECTION_TYPE)
            .metadata(document)
            .build();
    }

    private List<String> keysAtHop(List<GraphNodeMatch> matches, int hop) {
        return matches.stream()
            .filter(match -> match.hop() == hop)
            .map(GraphNodeMatch::key)
            .toList();
    }
}
