package com.tech.n.ai.batch.graph.extract;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphTypeWhitelist 단위 테스트
 *
 * 추출 결과 객체를 직접 만들어 넣으므로 OpenAI를 부르지 않는다.
 * LLMGraphTransformer는 목록 밖 타입도 그대로 통과시키기 때문에, 정해둔 타입만 저장된다는
 * 성질이 실제로 여기서 만들어지는지 확인한다.
 */
@DisplayName("GraphTypeWhitelist 단위 테스트")
class GraphTypeWhitelistTest {

    private static final Document SOURCE = Document.from("본문");

    @Nested
    @DisplayName("filter - 노드 거르기")
    class NodeFiltering {

        @Test
        @DisplayName("허용 목록에 있는 노드 타입만 통과한다")
        void keepsAllowedNodeTypes() {
            // Given
            GraphNode company = GraphNode.from("OpenAI", "Company");
            GraphNode model = GraphNode.from("GPT-4o", "Model");

            // When
            ExtractedGraph result = GraphTypeWhitelist.filter(
                GraphDocument.from(Set.of(company, model), Set.of(), SOURCE));

            // Then
            assertThat(result.nodes())
                .extracting(ExtractedGraph.Node::type)
                .containsExactlyInAnyOrder(GraphNodeType.COMPANY, GraphNodeType.MODEL);
            assertThat(result.rejectedNodeTypes()).isEmpty();
        }

        @Test
        @DisplayName("대소문자와 공백이 달라도 같은 타입으로 본다")
        void ignoresCaseAndWhitespaceInTypeName() {
            // Given
            GraphNode company = GraphNode.from("OpenAI", " company ");
            GraphNode capability = GraphNode.from("Function Calling", "CAPABILITY");

            // When
            ExtractedGraph result = GraphTypeWhitelist.filter(
                GraphDocument.from(Set.of(company, capability), Set.of(), SOURCE));

            // Then
            assertThat(result.nodes())
                .extracting(ExtractedGraph.Node::type)
                .containsExactlyInAnyOrder(GraphNodeType.COMPANY, GraphNodeType.CAPABILITY);
        }

        @Test
        @DisplayName("목록 밖 노드 타입은 버리고 타입별 건수를 센다")
        void countsRejectedNodeTypes() {
            // Given: LLMGraphTransformer는 타입이 없으면 "Node"로 채워 넘긴다
            GraphNode person = GraphNode.from("Sam Altman", "Person");
            GraphNode unknown = GraphNode.from("무언가", "Node");
            GraphNode model = GraphNode.from("GPT-4o", "Model");

            // When
            ExtractedGraph result = GraphTypeWhitelist.filter(
                GraphDocument.from(Set.of(person, unknown, model), Set.of(), SOURCE));

            // Then
            assertThat(result.nodes()).hasSize(1);
            assertThat(result.rejectedNodeTypes()).containsExactlyInAnyOrderEntriesOf(
                Map.of("Person", 1, "Node", 1));
        }
    }

    @Nested
    @DisplayName("filter - 엣지 거르기")
    class EdgeFiltering {

        @Test
        @DisplayName("허용 목록에 있는 관계 타입만 통과한다")
        void keepsAllowedRelationTypes() {
            // Given
            GraphNode company = GraphNode.from("OpenAI", "Company");
            GraphNode model = GraphNode.from("GPT-4o", "Model");
            GraphEdge released = GraphEdge.from(company, model, "RELEASED");

            // When
            ExtractedGraph result = GraphTypeWhitelist.filter(
                GraphDocument.from(Set.of(company, model), Set.of(released), SOURCE));

            // Then
            assertThat(result.edges()).singleElement()
                .satisfies(edge -> {
                    assertThat(edge.type()).isEqualTo(GraphRelationType.RELEASED);
                    assertThat(edge.sourceName()).isEqualTo("OpenAI");
                    assertThat(edge.targetName()).isEqualTo("GPT-4o");
                });
            assertThat(result.rejectedRelationTypes()).isEmpty();
        }

        @Test
        @DisplayName("목록 밖 관계 타입은 버리고 타입별 건수를 센다")
        void countsRejectedRelationTypes() {
            // Given: COMPETES_WITH는 표본에서 한 번도 안 나와 타입 목록에서 뺐다. 모델이 그래도 뱉으면 버려야 한다.
            GraphNode company = GraphNode.from("OpenAI", "Company");
            GraphNode model = GraphNode.from("GPT-4o", "Model");
            GraphEdge competes = GraphEdge.from(company, model, "COMPETES_WITH");

            // When
            ExtractedGraph result = GraphTypeWhitelist.filter(
                GraphDocument.from(Set.of(company, model), Set.of(competes), SOURCE));

            // Then
            assertThat(result.edges()).isEmpty();
            assertThat(result.rejectedRelationTypes()).containsEntry("COMPETES_WITH", 1);
        }

        @Test
        @DisplayName("회사가 제품을 쓴다는 USES 관계가 방향 그대로 통과한다")
        void keepsUsesRelation() {
            // Given: 고객사 사례 문장. 받을 타입이 없던 때는 SUPPORTS로 방향까지 뒤집혀 들어갔다.
            GraphNode customer = GraphNode.from("Zapier", "Company");
            GraphNode product = GraphNode.from("ChatGPT Work", "Technology");
            GraphEdge uses = GraphEdge.from(customer, product, "USES");

            // When
            ExtractedGraph result = GraphTypeWhitelist.filter(
                GraphDocument.from(Set.of(customer, product), Set.of(uses), SOURCE));

            // Then
            assertThat(result.edges()).singleElement()
                .satisfies(edge -> {
                    assertThat(edge.type()).isEqualTo(GraphRelationType.USES);
                    assertThat(edge.sourceName()).isEqualTo("Zapier");
                    assertThat(edge.targetName()).isEqualTo("ChatGPT Work");
                });
        }

        @Test
        @DisplayName("양 끝 노드 중 하나가 버려지면 엣지도 버린다")
        void dropsEdgeWhenEndpointRejected() {
            // Given: Person은 허용 목록 밖이라 노드가 버려진다
            GraphNode person = GraphNode.from("Sam Altman", "Person");
            GraphNode model = GraphNode.from("GPT-4o", "Model");
            GraphEdge released = GraphEdge.from(person, model, "RELEASED");

            // When
            ExtractedGraph result = GraphTypeWhitelist.filter(
                GraphDocument.from(Set.of(person, model), Set.of(released), SOURCE));

            // Then
            assertThat(result.edges()).isEmpty();
            assertThat(result.rejectedNodeTypes()).containsEntry("Person", 1);
            assertThat(result.rejectedRelationTypes()).containsEntry("RELEASED", 1);
        }
    }
}
