package com.tech.n.ai.batch.graph.write;

import com.mongodb.ReadPreference;
import com.tech.n.ai.domain.mongodb.document.TechGraphEdgeDocument;
import com.tech.n.ai.domain.mongodb.document.TechGraphNodeDocument;
import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 그래프 노드·엣지를 MongoDB에 upsert한다.
 *
 * 키로 찾아 없으면 만들고 있으면 출처만 더한다. 이름과 생성 시각은 $setOnInsert라 처음 본 표기가
 * 그대로 남고, 출처는 $addToSet이라 같은 문서를 다시 처리해도 목록이 늘지 않는다.
 * 한 필드에 $set과 $setOnInsert를 같이 걸면 MongoDB가 연산자 충돌로 거절하므로 섞지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GraphWriter {

    public static final String NODE_COLLECTION = "tech_graph_nodes";
    public static final String EDGE_COLLECTION = "tech_graph_edges";

    private final MongoTemplate mongoTemplate;

    public void upsertNode(String nodeKey, GraphNodeType type, String name, String externalId) {
        LocalDateTime now = LocalDateTime.now();
        Query query = new Query(Criteria.where("key").is(nodeKey));
        Update update = new Update()
            .setOnInsert("key", nodeKey)
            .setOnInsert("type", type.label())
            .setOnInsert("name", name)
            .setOnInsert("created_at", now)
            .set("updated_at", now)
            .addToSet("external_ids", externalId);

        mongoTemplate.upsert(query, update, TechGraphNodeDocument.class);
    }

    public void upsertEdge(String edgeKey, GraphRelationType type,
                           String sourceKey, String targetKey, String externalId) {
        LocalDateTime now = LocalDateTime.now();
        Query query = new Query(Criteria.where("key").is(edgeKey));
        Update update = new Update()
            .setOnInsert("key", edgeKey)
            .setOnInsert("type", type.label())
            .setOnInsert("source_key", sourceKey)
            .setOnInsert("target_key", targetKey)
            .setOnInsert("created_at", now)
            .set("updated_at", now)
            .addToSet("external_ids", externalId);

        mongoTemplate.upsert(query, update, TechGraphEdgeDocument.class);
    }

    public long countNodes() {
        return count(NODE_COLLECTION);
    }

    public long countEdges() {
        return count(EDGE_COLLECTION);
    }

    /**
     * 방금 쓴 것까지 세야 하므로 primary에서 읽는다. MongoClientConfig의 기본 readPreference가
     * secondaryPreferred라 그대로 세면 복제 지연 때문에 두 번째 실행의 카운트가 흔들린다.
     */
    private long count(String collectionName) {
        return mongoTemplate.getCollection(collectionName)
            .withReadPreference(ReadPreference.primary())
            .countDocuments();
    }
}
