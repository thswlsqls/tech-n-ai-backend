package com.tech.n.ai.batch.graph.key;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;
import com.tech.n.ai.domain.mongodb.key.GraphKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphKeys 단위 테스트
 *
 * 키 생성은 Spring도 MongoDB도 OpenAI도 쓰지 않는 순수 계산이라 그대로 돈다.
 */
@DisplayName("GraphKeys 단위 테스트")
class GraphKeysTest {

    @Nested
    @DisplayName("nodeKey - 노드 키 생성")
    class NodeKey {

        @Test
        @DisplayName("타입 라벨과 정규화한 이름을 | 로 잇는다")
        void joinsTypeLabelAndNormalizedName() {
            // Given
            String rawName = "GPT-4o";

            // When
            String key = GraphKeys.nodeKey(GraphNodeType.MODEL, rawName).orElseThrow();

            // Then
            assertThat(key).isEqualTo("Model|gpt-4o");
        }

        @Test
        @DisplayName("이름 앞뒤 공백을 없앤다")
        void stripsSurroundingWhitespace() {
            // Given
            String rawName = "  OpenAI  ";

            // When
            String key = GraphKeys.nodeKey(GraphNodeType.COMPANY, rawName).orElseThrow();

            // Then
            assertThat(key).isEqualTo("Company|openai");
        }

        @Test
        @DisplayName("이름 가운데 연속 공백을 한 칸으로 줄인다")
        void collapsesInnerWhitespace() {
            // Given
            String rawName = "Function   Calling";

            // When
            String key = GraphKeys.nodeKey(GraphNodeType.CAPABILITY, rawName).orElseThrow();

            // Then
            assertThat(key).isEqualTo("Capability|function calling");
        }

        @Test
        @DisplayName("대소문자만 다른 이름은 같은 키가 된다")
        void ignoresCase() {
            // Given
            String upperCase = "OPENAI";
            String mixedCase = "OpenAi";

            // When
            String upperKey = GraphKeys.nodeKey(GraphNodeType.COMPANY, upperCase).orElseThrow();
            String mixedKey = GraphKeys.nodeKey(GraphNodeType.COMPANY, mixedCase).orElseThrow();

            // Then
            assertThat(upperKey).isEqualTo(mixedKey);
        }

        @Test
        @DisplayName("이름이 비면 키를 만들지 않는다")
        void blankNameProducesNoKey() {
            // Given
            String blankName = "   ";

            // When & Then
            assertThat(GraphKeys.nodeKey(GraphNodeType.MODEL, blankName)).isEmpty();
            assertThat(GraphKeys.nodeKey(GraphNodeType.MODEL, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("edgeKey - 엣지 키 생성")
    class EdgeKey {

        @Test
        @DisplayName("출발·관계·도착 순서로 잇는다")
        void joinsSourceRelationTarget() {
            // Given
            String sourceKey = "Company|openai";
            String targetKey = "Model|gpt-4o";

            // When
            String key = GraphKeys.edgeKey(sourceKey, GraphRelationType.RELEASED, targetKey);

            // Then
            assertThat(key).isEqualTo("Company|openai->RELEASED->Model|gpt-4o");
        }

        @Test
        @DisplayName("출발과 도착을 바꾸면 다른 키가 된다")
        void keepsDirection() {
            // Given
            String companyKey = "Company|openai";
            String modelKey = "Model|gpt-4o";

            // When
            String forward = GraphKeys.edgeKey(companyKey, GraphRelationType.RELEASED, modelKey);
            String backward = GraphKeys.edgeKey(modelKey, GraphRelationType.RELEASED, companyKey);

            // Then
            assertThat(forward).isNotEqualTo(backward);
        }
    }

    @Nested
    @DisplayName("normalizeName - 이름 정규화")
    class NormalizeName {

        @Test
        @DisplayName("앞뒤 공백 제거와 연속 공백 축약과 소문자 변환을 한 번에 한다")
        void normalizesAtOnce() {
            // Given
            String rawName = "  Claude   Sonnet  ";

            // When & Then
            assertThat(GraphKeys.normalizeName(rawName)).contains("claude sonnet");
        }

        @Test
        @DisplayName("남는 글자가 없으면 빈 값을 준다")
        void emptyAfterNormalization() {
            // When & Then
            assertThat(GraphKeys.normalizeName("\t\n ")).isEmpty();
        }
    }
}
