package com.tech.n.ai.domain.mongodb.service;

import com.tech.n.ai.domain.mongodb.service.dto.GraphNodeMatch;
import lombok.RequiredArgsConstructor;
import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 지식 그래프 노드·엣지를 읽는다.
 *
 * 읽기 전용이다. aggregate 말고는 아무것도 부르지 않는다. 질의 구조는 코드에 고정돼 있고
 * 사용자가 쓴 문자열은 $in 배열이나 정규식 값 자리에만 들어간다.
 */
@Service
@RequiredArgsConstructor
public class TechGraphReader {

    public static final String COLLECTION_NODES = "tech_graph_nodes";
    public static final String COLLECTION_EDGES = "tech_graph_edges";

    /** 허브 노드다. 하나가 문서 수백 건을 가리켜 확장 경로에서 뺀다. 0홉 기여는 그대로 둔다. */
    private static final String HUB_NODE_TYPE = "Company";

    /** 정규식에서 뜻이 달라지는 문자들. 리터럴로 쓰려면 앞에 역슬래시를 붙여야 한다. */
    private static final String REGEX_METACHARACTERS = "\\.^$|?*+()[]{}";

    private final MongoTemplate mongoTemplate;

    /**
     * 후보 키와 부분 일치 리터럴로 시드 노드를 찾고, 엣지를 한 번 타서 이웃까지 가져온다.
     *
     * @param candidateKeys 질문에서 만든 노드 키 후보
     * @param nameLiterals 키 안에 들어 있으면 맞는 것으로 볼 부분 문자열(버전 번호 등)
     * @param maxSeeds 시드 노드 개수 상한
     * @param maxEdgesPerSeed 시드 하나당 훑을 엣지 개수 상한
     * @param maxTimeMs 서버 쪽 실행 시간 상한
     * @return 시드(0홉)와 이웃(1홉)을 합친 목록. 같은 키는 더 가까운 홉 하나만 남는다.
     */
    public List<GraphNodeMatch> findMatches(
            List<String> candidateKeys,
            List<String> nameLiterals,
            int maxSeeds,
            int maxEdgesPerSeed,
            long maxTimeMs) {

        boolean noKeys = candidateKeys == null || candidateKeys.isEmpty();
        boolean noLiterals = nameLiterals == null || nameLiterals.isEmpty();
        if (noKeys && noLiterals) {
            return List.of();
        }

        List<Document> pipeline = buildPipeline(candidateKeys, nameLiterals, maxSeeds, maxEdgesPerSeed);

        List<Document> seeds = mongoTemplate.getCollection(COLLECTION_NODES)
            .aggregate(pipeline)
            .maxTime(maxTimeMs, TimeUnit.MILLISECONDS)
            .into(new ArrayList<>());

        return flatten(seeds);
    }

    /**
     * 시드 조회 → 엣지 조회 → 이웃 노드 조회를 한 번의 aggregation으로 묶는다.
     *
     * $lookup에 localField/foreignField와 pipeline을 함께 쓰는 문법은 MongoDB 5.0부터다.
     * 공식 문서: https://www.mongodb.com/docs/manual/reference/operator/aggregation/lookup/
     */
    static List<Document> buildPipeline(
            List<String> candidateKeys,
            List<String> nameLiterals,
            int maxSeeds,
            int maxEdgesPerSeed) {

        List<Document> keyConditions = new ArrayList<>();
        if (candidateKeys != null && !candidateKeys.isEmpty()) {
            keyConditions.add(new Document("key", new Document("$in", candidateKeys)));
        }
        if (nameLiterals != null) {
            for (String literal : nameLiterals) {
                keyConditions.add(new Document("key",
                    new BsonRegularExpression(escapeLiteral(literal))));
            }
        }

        Document nodeProjection = new Document()
            .append("_id", 0)
            .append("key", 1)
            .append("type", 1)
            .append("name", 1)
            .append("external_ids", 1);

        Document edgeMatch = new Document("$expr", new Document("$and", List.of(
            new Document("$ne", List.of("$$st", HUB_NODE_TYPE)),
            new Document("$or", List.of(
                new Document("$eq", List.of("$source_key", "$$sk")),
                new Document("$eq", List.of("$target_key", "$$sk"))
            ))
        )));

        Document edgeLookup = new Document("$lookup", new Document()
            .append("from", COLLECTION_EDGES)
            .append("let", new Document().append("sk", "$key").append("st", "$type"))
            .append("pipeline", List.of(
                new Document("$match", edgeMatch),
                new Document("$limit", maxEdgesPerSeed),
                new Document("$project", new Document()
                    .append("_id", 0)
                    .append("source_key", 1)
                    .append("target_key", 1))
            ))
            .append("as", "edges"));

        Document neighborKeys = new Document("$setDifference", List.of(
            new Document("$setUnion", List.of("$edges.source_key", "$edges.target_key")),
            List.of("$key")
        ));

        Document neighborLookup = new Document("$lookup", new Document()
            .append("from", COLLECTION_NODES)
            .append("localField", "neighbor_keys")
            .append("foreignField", "key")
            .append("pipeline", List.of(
                new Document("$match", new Document("type", new Document("$ne", HUB_NODE_TYPE))),
                new Document("$project", nodeProjection)
            ))
            .append("as", "neighbors"));

        Document finalProjection = new Document(nodeProjection).append("neighbors", 1);

        return List.of(
            new Document("$match", new Document("$or", keyConditions)),
            new Document("$limit", maxSeeds),
            new Document("$project", nodeProjection),
            edgeLookup,
            new Document("$addFields", new Document("neighbor_keys", neighborKeys)),
            neighborLookup,
            new Document("$project", finalProjection)
        );
    }

    /**
     * 정규식 메타문자 앞에 역슬래시를 붙인다.
     *
     * Pattern.quote()의 \Q...\E는 쓰지 않는다. 리터럴 안에 \E가 들어오면 인용이 그 자리에서 끊긴다.
     */
    static String escapeLiteral(String literal) {
        StringBuilder escaped = new StringBuilder(literal.length() + 8);
        for (char c : literal.toCharArray()) {
            if (REGEX_METACHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    /**
     * 시드 문서와 그 안에 담긴 이웃을 한 줄짜리 목록으로 편다.
     * 같은 키가 시드로도 이웃으로도 나오면 홉이 작은 쪽만 남긴다.
     */
    private List<GraphNodeMatch> flatten(List<Document> seeds) {
        Map<String, GraphNodeMatch> byKey = new LinkedHashMap<>();
        for (Document seed : seeds) {
            putIfCloser(byKey, toMatch(seed, 0));
        }
        for (Document seed : seeds) {
            List<Document> neighbors = seed.getList("neighbors", Document.class, List.of());
            for (Document neighbor : neighbors) {
                putIfCloser(byKey, toMatch(neighbor, 1));
            }
        }
        return List.copyOf(byKey.values());
    }

    private void putIfCloser(Map<String, GraphNodeMatch> byKey, GraphNodeMatch match) {
        if (match.key() == null) {
            return;
        }
        GraphNodeMatch existing = byKey.get(match.key());
        if (existing == null || match.hop() < existing.hop()) {
            byKey.put(match.key(), match);
        }
    }

    private GraphNodeMatch toMatch(Document node, int hop) {
        return new GraphNodeMatch(
            node.getString("key"),
            node.getString("type"),
            node.getString("name"),
            List.copyOf(node.getList("external_ids", String.class, List.of())),
            hop
        );
    }
}
