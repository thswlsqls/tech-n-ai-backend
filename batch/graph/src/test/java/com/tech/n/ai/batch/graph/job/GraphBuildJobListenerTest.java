package com.tech.n.ai.batch.graph.job;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.tech.n.ai.batch.graph.write.GraphWriter;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GraphBuildJobListener 단위 테스트
 *
 * MongoTemplate과 컬렉션 핸들을 목으로 두므로 Atlas에 실제 인덱스를 만들지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GraphBuildJobListener 단위 테스트")
class GraphBuildJobListenerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoCollection<Document> collection;

    private GraphBuildJobListener listener;

    @BeforeEach
    void setUp() {
        listener = new GraphBuildJobListener(mongoTemplate);
        when(mongoTemplate.getCollection(anyString())).thenReturn(collection);
    }

    @Nested
    @DisplayName("beforeJob - API 키 확인")
    class ApiKeyGuard {

        @Test
        @DisplayName("키가 비어 있으면 잡을 시작하지 않는다")
        void blankKey_throws() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", "   ");

            // When & Then
            assertThatThrownBy(() -> listener.beforeJob(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat-model.api-key");
        }

        @Test
        @DisplayName("키가 null이면 잡을 시작하지 않는다")
        void nullKey_throws() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", null);

            // When & Then
            assertThatThrownBy(() -> listener.beforeJob(null))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("키가 있으면 그대로 통과한다")
        void presentKey_passes() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", "sk-test-key");

            // When & Then
            assertThatCode(() -> listener.beforeJob(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("키가 없으면 인덱스도 만들지 않는다")
        void blankKey_skipsIndexCreation() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", "");

            // When
            assertThatThrownBy(() -> listener.beforeJob(null))
                .isInstanceOf(IllegalStateException.class);

            // Then
            verify(mongoTemplate, never()).getCollection(anyString());
        }
    }

    @Nested
    @DisplayName("beforeJob - unique 인덱스 생성")
    class IndexCreation {

        @Test
        @DisplayName("그래프 컬렉션 두 개에만 key unique 인덱스를 만든다")
        void createsUniqueKeyIndexOnGraphCollectionsOnly() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", "sk-test-key");

            // When
            listener.beforeJob(null);

            // Then
            ArgumentCaptor<String> collectionCaptor = ArgumentCaptor.forClass(String.class);
            verify(mongoTemplate, times(2)).getCollection(collectionCaptor.capture());
            assertThat(collectionCaptor.getAllValues()).containsExactlyInAnyOrder(
                GraphWriter.NODE_COLLECTION, GraphWriter.EDGE_COLLECTION);

            ArgumentCaptor<IndexOptions> optionsCaptor = ArgumentCaptor.forClass(IndexOptions.class);
            verify(collection, times(2)).createIndex(any(Bson.class), optionsCaptor.capture());
            assertThat(optionsCaptor.getAllValues()).allMatch(IndexOptions::isUnique);
        }

        @Test
        @DisplayName("emerging_techs 같은 다른 컬렉션은 건드리지 않는다")
        void doesNotTouchOtherCollections() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", "sk-test-key");

            // When
            listener.beforeJob(null);

            // Then
            verify(mongoTemplate, never()).getCollection("emerging_techs");
            verify(mongoTemplate, never()).getCollection("conversation_sessions");
            verify(mongoTemplate, never()).getCollection("conversation_messages");
        }
    }

    @Nested
    @DisplayName("beforeJob - 그래프 컬렉션 비우기")
    class Reset {

        @Test
        @DisplayName("reset이 켜지면 그래프 컬렉션 두 개만 비우고 인덱스를 다시 만든다")
        void resetDropsOnlyGraphCollections() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", "sk-test-key");
            ReflectionTestUtils.setField(listener, "reset", true);

            // When
            listener.beforeJob(null);

            // Then: drop 두 번, 인덱스 두 번. 손댄 컬렉션은 그래프 두 개뿐이다.
            verify(collection, times(2)).drop();

            ArgumentCaptor<String> collectionCaptor = ArgumentCaptor.forClass(String.class);
            verify(mongoTemplate, times(4)).getCollection(collectionCaptor.capture());
            assertThat(collectionCaptor.getAllValues()).containsExactly(
                GraphWriter.NODE_COLLECTION, GraphWriter.EDGE_COLLECTION,
                GraphWriter.NODE_COLLECTION, GraphWriter.EDGE_COLLECTION);
        }

        @Test
        @DisplayName("reset이 꺼져 있으면 아무것도 비우지 않는다")
        void withoutResetNothingIsDropped() {
            // Given
            ReflectionTestUtils.setField(listener, "apiKey", "sk-test-key");
            ReflectionTestUtils.setField(listener, "reset", false);

            // When
            listener.beforeJob(null);

            // Then
            verify(collection, never()).drop();
        }
    }
}
