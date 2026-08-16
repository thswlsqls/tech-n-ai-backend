package com.tech.n.ai.batch.graph.extract;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GraphExtractor 단위 테스트
 *
 * ChatModel을 목으로 두어 OpenAI를 부르지 않는다. 목이 돌려주는 JSON은 실제 응답 형식
 * (head·head_type·relation·tail·tail_type)을 그대로 흉내낸 것이다.
 *
 * 생성자를 실제로 부르는 테스트가 여기에만 있다. LLMGraphTransformer는 생성자에서 examples를
 * 필수로 검사하는데, 다른 테스트는 전부 GraphExtractor를 목으로 대신해서 이 검사를 지나쳤고
 * 그 결과 잡을 실제로 띄울 때까지 "examples cannot be null"이 드러나지 않았다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GraphExtractor 단위 테스트")
class GraphExtractorTest {

    @Nested
    @DisplayName("생성자 - LLMGraphTransformer 조립")
    class Construction {

        @Test
        @DisplayName("빌더에 필요한 값이 다 차 있어 예외 없이 만들어진다")
        void buildsWithoutException() {
            // Given
            ChatModel chatModel = mock(ChatModel.class);

            // When & Then: examples가 빠지면 IllegalArgumentException으로 죽는다
            assertThatCode(() -> new GraphExtractor(chatModel)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("allowedNodes / allowedRelationships - enum이 유일한 출처")
    class AllowedTypes {

        @Test
        @DisplayName("허용 노드 목록이 GraphNodeType의 라벨과 같다")
        void nodesComeFromEnum() {
            // Given
            List<String> expected = Arrays.stream(GraphNodeType.values()).map(GraphNodeType::label).toList();

            // When
            List<String> actual = GraphExtractor.allowedNodes();

            // Then
            assertThat(actual).containsExactlyElementsOf(expected);
        }

        @Test
        @DisplayName("허용 관계 목록이 GraphRelationType의 라벨과 같다")
        void relationshipsComeFromEnum() {
            // Given
            List<String> expected =
                Arrays.stream(GraphRelationType.values()).map(GraphRelationType::label).toList();

            // When
            List<String> actual = GraphExtractor.allowedRelationships();

            // Then
            assertThat(actual).containsExactlyElementsOf(expected);
        }
    }

    @Nested
    @DisplayName("extract - 추출 결과 감싸기")
    class Extract {

        @Test
        @DisplayName("모델이 관계를 돌려주면 노드와 엣지가 담긴다")
        void wrapsExtractedGraph() {
            // Given
            ChatModel chatModel = chatModelReturning("""
                [{"head": "OpenAI", "head_type": "Company", "relation": "RELEASED", \
                "tail": "GPT-4o", "tail_type": "Model"}]
                """);

            // When
            Optional<GraphDocument> result = new GraphExtractor(chatModel).extract("본문");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().nodes()).hasSize(2);
            assertThat(result.get().relationships()).hasSize(1);
        }

        @Test
        @DisplayName("뽑을 게 없으면 빈 Optional이다")
        void emptyWhenNothingExtracted() {
            // Given: 관계가 하나도 없는 응답이면 transform()이 null을 돌려준다
            ChatModel chatModel = chatModelReturning("[]");

            // When
            Optional<GraphDocument> result = new GraphExtractor(chatModel).extract("본문");

            // Then
            assertThat(result).isEmpty();
        }
    }

    private ChatModel chatModelReturning(String json) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyList()))
            .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(json)).build());
        return chatModel;
    }
}
