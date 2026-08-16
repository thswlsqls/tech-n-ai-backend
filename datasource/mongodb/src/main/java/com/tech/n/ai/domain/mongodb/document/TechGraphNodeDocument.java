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
 * 지식 그래프 노드 Document (MongoDB)
 *
 * key에 거는 unique 인덱스는 여기에 @Indexed로 선언하지 않는다. MongoClientConfig가 상속한
 * AbstractMongoClientConfiguration은 autoIndexCreation()이 false라 선언해도 인덱스가 생기지 않고
 * 있는 것처럼 보이기만 한다. 인덱스는 그래프 구축 배치가 드라이버로 직접 만든다.
 */
@Document(collection = "tech_graph_nodes")
@Getter
@Setter
public class TechGraphNodeDocument {

    @Id
    private ObjectId id;

    @Field("key")
    private String key;  // 타입 라벨 + "|" + 정규화한 이름. 예: Model|gpt-4o

    @Field("type")
    private String type;  // GraphNodeType label

    @Field("name")
    private String name;  // 처음 저장될 때 본 원본 표기

    @Field("external_ids")
    private List<String> externalIds;  // 이 노드가 나온 emerging_techs 문서들

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;
}
