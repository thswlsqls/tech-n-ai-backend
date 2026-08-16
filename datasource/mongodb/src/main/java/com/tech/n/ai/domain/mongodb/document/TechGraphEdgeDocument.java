package com.tech.n.ai.domain.mongodb.document;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 지식 그래프 엣지 Document (MongoDB)
 *
 * key에 거는 unique 인덱스는 여기에 @Indexed로 선언하지 않는다. MongoClientConfig가 상속한
 * AbstractMongoClientConfiguration은 autoIndexCreation()이 false라 선언해도 인덱스가 생기지 않고
 * 있는 것처럼 보이기만 한다. 인덱스는 그래프 구축 배치가 드라이버로 직접 만든다.
 */
@Document(collection = "tech_graph_edges")
@Getter
@Setter
public class TechGraphEdgeDocument {

    @Id
    private ObjectId id;

    @Field("key")
    private String key;  // 출발 노드 키 + "->" + 관계 라벨 + "->" + 도착 노드 키

    @Field("type")
    private String type;  // GraphRelationType label

    @Field("source_key")
    private String sourceKey;  // 출발 노드의 key

    @Field("target_key")
    private String targetKey;  // 도착 노드의 key

    @Field("external_ids")
    private List<String> externalIds;  // 이 엣지가 나온 emerging_techs 문서들

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;
}
