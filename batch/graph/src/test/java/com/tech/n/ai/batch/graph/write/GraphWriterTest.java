package com.tech.n.ai.batch.graph.write;

import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.document.TechGraphEdgeDocument;
import com.tech.n.ai.domain.mongodb.document.TechGraphNodeDocument;
import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * GraphWriter 단위 테스트
 *
 * MongoTemplate을 목으로 두고 upsert에 넘어간 질의와 변경 연산자를 그대로 들여다본다.
 * 실제 Atlas에 붙지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GraphWriter 단위 테스트")
class GraphWriterTest {

    private static final String NODE_KEY = "Model|gpt-4o";
    private static final String EDGE_KEY = "Company|openai->RELEASED->Model|gpt-4o";

    @Mock
    private MongoTemplate mongoTemplate;

    private GraphWriter graphWriter;

    @BeforeEach
    void setUp() {
        graphWriter = new GraphWriter(mongoTemplate);
    }

    @Nested
    @DisplayName("upsertNode - 노드 저장")
    class UpsertNode {

        @Test
        @DisplayName("출처 목록은 $addToSet으로 더한다")
        void addsExternalIdToSet() {
            // Given
            String externalId = "ext-1";

            // When
            graphWriter.upsertNode(NODE_KEY, GraphNodeType.MODEL, "GPT-4o", externalId);

            // Then
            Document addToSet = capturedUpdate().get("$addToSet", Document.class);
            assertThat(addToSet.getString("external_ids")).isEqualTo(externalId);
        }

        @Test
        @DisplayName("이름과 생성 시각은 $setOnInsert라 처음 본 값이 남는다")
        void keepsFirstSeenNameAndCreatedAt() {
            // When
            graphWriter.upsertNode(NODE_KEY, GraphNodeType.MODEL, "GPT-4o", "ext-1");

            // Then
            Document update = capturedUpdate();
            Document setOnInsert = update.get("$setOnInsert", Document.class);
            assertThat(setOnInsert.getString("name")).isEqualTo("GPT-4o");
            assertThat(setOnInsert.getString("type")).isEqualTo("Model");
            assertThat(setOnInsert).containsKey("created_at");
            assertThat(update.get("$set", Document.class)).containsKey("updated_at");
        }

        @Test
        @DisplayName("한 필드에 $set과 $setOnInsert를 같이 걸지 않는다")
        void doesNotMixSetAndSetOnInsertOnSameField() {
            // Given: MongoDB는 같은 필드에 두 연산자가 걸리면 연산자 충돌로 거절한다

            // When
            graphWriter.upsertNode(NODE_KEY, GraphNodeType.MODEL, "GPT-4o", "ext-1");

            // Then
            Document update = capturedUpdate();
            assertThat(update.get("$set", Document.class).keySet())
                .doesNotContainAnyElementsOf(update.get("$setOnInsert", Document.class).keySet());
            assertThat(update.get("$setOnInsert", Document.class)).doesNotContainKey("external_ids");
            assertThat(update.get("$set", Document.class)).doesNotContainKey("external_ids");
        }

        @Test
        @DisplayName("같은 노드를 두 번 처리해도 같은 key로 찾는다")
        void findsSameKeyOnRepeat() {
            // When: 같은 대상이 다른 문서에서 다시 나온 상황
            graphWriter.upsertNode(NODE_KEY, GraphNodeType.MODEL, "GPT-4o", "ext-1");
            graphWriter.upsertNode(NODE_KEY, GraphNodeType.MODEL, "gpt-4o", "ext-2");

            // Then
            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
            verify(mongoTemplate, times(2))
                .upsert(queryCaptor.capture(), any(Update.class), eq(TechGraphNodeDocument.class));
            assertThat(queryCaptor.getAllValues())
                .extracting(query -> query.getQueryObject().getString("key"))
                .containsExactly(NODE_KEY, NODE_KEY);
        }
    }

    @Nested
    @DisplayName("upsertEdge - 엣지 저장")
    class UpsertEdge {

        @Test
        @DisplayName("출발·도착 노드 키와 생성 시각은 $setOnInsert이고 출처는 $addToSet이다")
        void writesEndpointsOnInsert() {
            // When
            graphWriter.upsertEdge(EDGE_KEY, GraphRelationType.RELEASED,
                "Company|openai", "Model|gpt-4o", "ext-1");

            // Then
            ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
            verify(mongoTemplate)
                .upsert(any(Query.class), updateCaptor.capture(), eq(TechGraphEdgeDocument.class));

            Document update = updateCaptor.getValue().getUpdateObject();
            Document setOnInsert = update.get("$setOnInsert", Document.class);
            assertThat(setOnInsert.getString("source_key")).isEqualTo("Company|openai");
            assertThat(setOnInsert.getString("target_key")).isEqualTo("Model|gpt-4o");
            assertThat(update.get("$addToSet", Document.class).getString("external_ids"))
                .isEqualTo("ext-1");
        }
    }

    @Nested
    @DisplayName("쓰기 대상 컬렉션")
    class WriteTarget {

        @Test
        @DisplayName("그래프 Document 2종에만 쓰고 emerging_techs는 건드리지 않는다")
        void writesOnlyToGraphDocuments() {
            // When
            graphWriter.upsertNode(NODE_KEY, GraphNodeType.MODEL, "GPT-4o", "ext-1");
            graphWriter.upsertEdge(EDGE_KEY, GraphRelationType.RELEASED,
                "Company|openai", "Model|gpt-4o", "ext-1");

            // Then
            verify(mongoTemplate).upsert(any(Query.class), any(Update.class),
                eq(TechGraphNodeDocument.class));
            verify(mongoTemplate).upsert(any(Query.class), any(Update.class),
                eq(TechGraphEdgeDocument.class));
            verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class),
                eq(EmergingTechDocument.class));
            verify(mongoTemplate, never()).save(any());
        }
    }

    private Document capturedUpdate() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate)
            .upsert(any(Query.class), updateCaptor.capture(), eq(TechGraphNodeDocument.class));
        return updateCaptor.getValue().getUpdateObject();
    }
}
