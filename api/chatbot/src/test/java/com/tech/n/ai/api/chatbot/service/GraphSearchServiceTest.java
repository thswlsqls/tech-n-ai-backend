package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.domain.mongodb.service.TechGraphReader;
import com.tech.n.ai.domain.mongodb.service.dto.GraphNodeMatch;
import com.tech.n.ai.domain.mongodb.util.VectorSearchUtil;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * GraphSearchService 단위 테스트
 *
 * MongoDB Atlas도 OpenAI도 붙지 않는다. 그래프 조회 결과를 그대로 넣고, 무엇을 읽고
 * 어떤 모양으로 내놓는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GraphSearchService 단위 테스트")
class GraphSearchServiceTest {

    @Mock
    private TechGraphReader techGraphReader;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private GraphSearchService graphSearchService;

    private static final String OID_FIRST = "aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OID_SECOND = "bbbbbbbbbbbbbbbbbbbbbbbb";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(graphSearchService, "graphEnabled", true);
        ReflectionTestUtils.setField(graphSearchService, "maxResults", 10);
        ReflectionTestUtils.setField(graphSearchService, "maxSeeds", 20);
        ReflectionTestUtils.setField(graphSearchService, "maxEdgesPerSeed", 20);
        ReflectionTestUtils.setField(graphSearchService, "maxTimeMs", 2000L);
    }

    @Nested
    @DisplayName("search - 그래프 경로 스위치")
    class Switch {

        @Test
        @DisplayName("꺼져 있으면 그래프도 MongoDB도 건드리지 않는다")
        void disabled_touchesNothing() {
            // Given
            ReflectionTestUtils.setField(graphSearchService, "graphEnabled", false);

            // When
            GraphSearchOutcome outcome = graphSearchService.search("OpenAI SDK v2.26.0 비교");

            // Then
            assertThat(outcome.enabled()).isFalse();
            assertThat(outcome.results()).isEmpty();
            assertThat(outcome.externalIds()).isEmpty();
            verifyNoInteractions(techGraphReader, mongoTemplate);
        }

        @Test
        @DisplayName("걸린 노드가 없으면 원본 문서를 읽지 않는다")
        void noMatches_skipsDocumentQuery() {
            // Given
            when(techGraphReader.findMatches(any(), any(), anyInt(), anyInt(), anyLong()))
                .thenReturn(List.of());

            // When
            GraphSearchOutcome outcome = graphSearchService.search("아무 말");

            // Then
            assertThat(outcome.enabled()).isTrue();
            assertThat(outcome.results()).isEmpty();
            verifyNoInteractions(mongoTemplate);
        }
    }

    @Nested
    @DisplayName("search - 결과 만들기")
    class BuildingResults {

        @Test
        @DisplayName("순위 순서대로 결과를 세우고 홉별 노드 키를 나눠 담는다")
        void ordersResultsByRank() {
            // Given: MongoDB는 순서를 지켜주지 않으므로 뒤집힌 순서로 돌려준다
            when(techGraphReader.findMatches(any(), any(), anyInt(), anyInt(), anyLong()))
                .thenReturn(List.of(
                    new GraphNodeMatch("Company|openai", "Company", "OpenAI",
                        List.of("github:1", "github:2"), 0),
                    new GraphNodeMatch("Release|sdk v2.26.0", "Release", "SDK v2.26.0",
                        List.of("github:1"), 1)
                ));
            when(mongoTemplate.find(any(Query.class), eq(Document.class),
                eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(
                    emergingTech(OID_SECOND, "github:2", "두 번째 문서"),
                    emergingTech(OID_FIRST, "github:1", "첫 번째 문서")
                ));

            // When
            GraphSearchOutcome outcome = graphSearchService.search("OpenAI SDK v2.26.0 비교");

            // Then: github:1이 노드 둘에게서 나와 1순위
            assertThat(outcome.externalIds()).containsExactly("github:1", "github:2");
            assertThat(outcome.results()).extracting(SearchResult::documentId)
                .containsExactly(OID_FIRST, OID_SECOND);
            assertThat(outcome.seedKeys()).containsExactly("Company|openai");
            assertThat(outcome.expandedKeys()).containsExactly("Release|sdk v2.26.0");
            assertThat(outcome.capped()).isFalse();
        }

        @Test
        @DisplayName("SearchResult가 벡터 검색 결과와 같은 모양이다")
        void searchResultShapeMatchesVectorSearch() {
            // Given
            when(techGraphReader.findMatches(any(), any(), anyInt(), anyInt(), anyLong()))
                .thenReturn(List.of(new GraphNodeMatch(
                    "Company|openai", "Company", "OpenAI", List.of("github:1"), 0)));
            Document document = emergingTech(OID_FIRST, "github:1", "본문");
            when(mongoTemplate.find(any(Query.class), eq(Document.class),
                eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(document));

            // When
            SearchResult result = graphSearchService.search("OpenAI").results().get(0);

            // Then: documentId는 _id 문자열, metadata는 원본 Document 그대로
            assertThat(result.documentId()).isEqualTo(OID_FIRST);
            assertThat(result.text()).isEqualTo("본문");
            assertThat(result.collectionType()).isEqualTo("EMERGING_TECH");
            assertThat(result.metadata()).isSameAs(document);
        }

        @Test
        @DisplayName("PUBLISHED 문서만 읽고 실행 시간 상한을 건다")
        void queriesPublishedOnlyWithMaxTime() {
            // Given
            when(techGraphReader.findMatches(any(), any(), anyInt(), anyInt(), anyLong()))
                .thenReturn(List.of(new GraphNodeMatch(
                    "Company|openai", "Company", "OpenAI", List.of("github:1"), 0)));
            when(mongoTemplate.find(any(Query.class), eq(Document.class),
                eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(emergingTech(OID_FIRST, "github:1", "본문")));

            // When
            graphSearchService.search("OpenAI");

            // Then
            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
            verify(mongoTemplate).find(queryCaptor.capture(), eq(Document.class),
                eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
            Query query = queryCaptor.getValue();
            assertThat(query.getQueryObject().toJson()).contains("PUBLISHED");
            assertThat(query.getMeta().getMaxTimeMsec()).isEqualTo(2000L);
        }
    }

    @Nested
    @DisplayName("search - 읽기 전용")
    class ReadOnly {

        @Test
        @DisplayName("MongoTemplate에 읽기 말고 다른 호출을 하지 않는다")
        void neverWrites() {
            // Given
            when(techGraphReader.findMatches(any(), any(), anyInt(), anyInt(), anyLong()))
                .thenReturn(List.of(new GraphNodeMatch(
                    "Company|openai", "Company", "OpenAI", List.of("github:1"), 0)));
            when(mongoTemplate.find(any(Query.class), eq(Document.class),
                eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(emergingTech(OID_FIRST, "github:1", "본문")));

            // When
            graphSearchService.search("OpenAI");

            // Then: find 한 번이 전부다. save·insert·update는 물론 다른 어떤 호출도 없다
            verify(mongoTemplate).find(any(Query.class), eq(Document.class),
                eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
            verifyNoMoreInteractions(mongoTemplate);
        }
    }

    private Document emergingTech(String oid, String externalId, String text) {
        return new Document()
            .append("_id", new ObjectId(oid))
            .append("external_id", externalId)
            .append("embedding_text", text)
            .append("status", "PUBLISHED");
    }
}
