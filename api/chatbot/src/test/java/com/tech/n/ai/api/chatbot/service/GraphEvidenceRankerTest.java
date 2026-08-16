package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.domain.mongodb.service.dto.GraphNodeMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphEvidenceRanker 단위 테스트
 *
 * 문서를 몇 번 세는지와 어떤 순서로 내놓는지가 전부다. MongoDB도 LLM도 쓰지 않는다.
 */
@DisplayName("GraphEvidenceRanker 단위 테스트")
class GraphEvidenceRankerTest {

    @Nested
    @DisplayName("rank - 문서 단위로 접기")
    class Folding {

        @Test
        @DisplayName("여러 노드가 가리킨 같은 문서를 한 번만 내놓는다")
        void sameDocumentFromManyNodes_appearsOnce() {
            // Given: 노드 두 개가 같은 문서를, 다른 노드 하나가 별개 문서를 가리킨다
            List<GraphNodeMatch> matches = List.of(
                node("Company|openai", List.of("github:1"), 0),
                node("Release|sdk v2.26.0", List.of("github:1"), 0),
                node("Model|gpt-4o", List.of("github:2"), 0)
            );

            // When
            GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(matches, 10);

            // Then: github:1이 중복 없이 한 번만, 그리고 두 노드가 가리켰으니 앞에 온다
            assertThat(ranked.externalIds()).containsExactly("github:1", "github:2");
            assertThat(ranked.capped()).isFalse();
        }

        @Test
        @DisplayName("같은 노드가 두 번 들어와도 한 번만 센다")
        void duplicatedNodeKey_countedOnce() {
            // Given: 키가 같은 노드가 두 번 들어온다
            List<GraphNodeMatch> matches = List.of(
                node("Company|openai", List.of("github:1"), 0),
                node("Company|openai", List.of("github:1"), 1),
                node("Model|gpt-4o", List.of("github:2"), 0),
                node("Model|gpt-4o-mini", List.of("github:2"), 0)
            );

            // When
            GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(matches, 10);

            // Then: github:2를 가리킨 노드가 둘이라 앞에 온다
            assertThat(ranked.externalIds()).containsExactly("github:2", "github:1");
        }
    }

    @Nested
    @DisplayName("rank - 정렬 규칙")
    class Ordering {

        @Test
        @DisplayName("가리킨 노드 수가 같으면 좁은 노드가 준 문서를 앞에 둔다")
        void sameCount_prefersNarrowNode() {
            // Given: 한쪽은 문서 세 건을 가리키는 넓은 노드, 다른 쪽은 한 건짜리 좁은 노드
            List<GraphNodeMatch> matches = List.of(
                node("Company|openai", List.of("github:wide"), 0),
                node("Company|anthropic", List.of("github:wide", "github:x", "github:y"), 0),
                node("Release|sdk v2.26.0", List.of("github:narrow"), 0)
            );

            // When
            GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(matches, 10);

            // Then: github:wide는 노드 둘이 가리켜 1순위, 나머지는 좁은 노드가 준 것부터
            assertThat(ranked.externalIds()).startsWith("github:wide", "github:narrow");
        }

        @Test
        @DisplayName("노드 수와 크기가 같으면 0홉이 1홉보다 앞에 온다")
        void sameCountAndSize_prefersSeedOverNeighbor() {
            // Given
            List<GraphNodeMatch> matches = List.of(
                node("Model|neighbor", List.of("github:hop1"), 1),
                node("Model|seed", List.of("github:hop0"), 0)
            );

            // When
            GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(matches, 10);

            // Then
            assertThat(ranked.externalIds()).containsExactly("github:hop0", "github:hop1");
        }

        @Test
        @DisplayName("모두 같으면 external_id 사전순으로 정한다")
        void allEqual_sortsAlphabetically() {
            // Given: 재실행해도 같은 순서가 나와야 한다
            List<GraphNodeMatch> matches = List.of(
                node("Model|a", List.of("github:c"), 0),
                node("Model|b", List.of("github:a"), 0),
                node("Model|c", List.of("github:b"), 0)
            );

            // When
            GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(matches, 10);

            // Then
            assertThat(ranked.externalIds()).containsExactly("github:a", "github:b", "github:c");
        }
    }

    @Nested
    @DisplayName("rank - 상한")
    class Capping {

        @Test
        @DisplayName("상한을 넘으면 뒤를 자르고 capped를 표시한다")
        void overLimit_cutsAndFlags() {
            // Given: 문서 세 건, 상한 두 건
            List<GraphNodeMatch> matches = List.of(
                node("Model|a", List.of("github:a"), 0),
                node("Model|b", List.of("github:b"), 0),
                node("Model|c", List.of("github:c"), 0)
            );

            // When
            GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(matches, 2);

            // Then
            assertThat(ranked.externalIds()).containsExactly("github:a", "github:b");
            assertThat(ranked.capped()).isTrue();
        }

        @Test
        @DisplayName("걸린 노드가 없으면 빈 결과를 준다")
        void noMatches_returnsEmpty() {
            // Given
            // When
            GraphEvidenceRanker.Ranked ranked = GraphEvidenceRanker.rank(List.of(), 10);

            // Then
            assertThat(ranked.externalIds()).isEmpty();
            assertThat(ranked.capped()).isFalse();
        }
    }

    private GraphNodeMatch node(String key, List<String> externalIds, int hop) {
        return new GraphNodeMatch(key, "Model", key, externalIds, hop);
    }
}
