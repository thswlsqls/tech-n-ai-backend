package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RetrievalService 단위 테스트
 *
 * Atlas도 OpenAI도 부르지 않는다. 두 검색 결과를 어떻게 합치는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetrievalService 단위 테스트")
class RetrievalServiceTest {

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private GraphSearchService graphSearchService;

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalService(vectorSearchService, graphSearchService);
        ReflectionTestUtils.setField(retrievalService, "graphEnabled", true);
    }

    private SearchResult result(String documentId, Double score) {
        return SearchResult.builder()
            .documentId(documentId)
            .text(documentId + " 본문")
            .score(score)
            .collectionType("EMERGING_TECH")
            .build();
    }

    private SearchOutcome vectorOutcome(List<SearchResult> results) {
        return SearchOutcome.builder()
            .path(SearchPath.HYBRID)
            .candidates(results)
            .recencyQueryFailed(false)
            .results(results)
            .build();
    }

    private GraphSearchOutcome graphOutcome(List<SearchResult> results) {
        return new GraphSearchOutcome(
            true,
            results,
            List.of("Model|gpt-4o"),
            List.of("Provider|openai"),
            results.stream().map(SearchResult::documentId).toList(),
            false,
            42L);
    }

    private void givenSearches(SearchOutcome vector, GraphSearchOutcome graph) {
        when(vectorSearchService.search(anyString(), anyLong(), any())).thenReturn(vector);
        when(graphSearchService.search(anyString())).thenReturn(graph);
    }

    private RetrievalOutcome retrieve() {
        return retrievalService.retrieve("질문", 0L, SearchOptions.builder().build());
    }

    @Nested
    @DisplayName("retrieve - 두 결과 합치기")
    class Merge {

        @Test
        @DisplayName("벡터 결과 뒤에 그래프 결과를 순서대로 붙인다")
        void appendsGraphResultsAfterVectorResults() {
            // Given
            givenSearches(
                vectorOutcome(List.of(result("v1", 0.9), result("v2", 0.8))),
                graphOutcome(List.of(result("g1", 0.5), result("g2", 0.33))));

            // When
            RetrievalOutcome outcome = retrieve();

            // Then
            assertThat(outcome.merged())
                .extracting(SearchResult::documentId)
                .containsExactly("v1", "v2", "g1", "g2");
        }

        @Test
        @DisplayName("벡터가 이미 물고 온 문서는 그래프 쪽에서 뺀다")
        void dropsGraphDocumentsAlreadyFoundByVector() {
            // Given
            givenSearches(
                vectorOutcome(List.of(result("v1", 0.9), result("dup", 0.8))),
                graphOutcome(List.of(result("dup", 0.5), result("g1", 0.33))));

            // When
            RetrievalOutcome outcome = retrieve();

            // Then
            assertThat(outcome.merged())
                .extracting(SearchResult::documentId)
                .containsExactly("v1", "dup", "g1");
        }

        @Test
        @DisplayName("그래프 문서 점수를 벡터 최저점 아래로 다시 매긴다")
        void rescoresGraphDocumentsBelowVectorFloor() {
            // Given: 벡터 최저점이 0.8이다
            givenSearches(
                vectorOutcome(List.of(result("v1", 0.9), result("v2", 0.8))),
                graphOutcome(List.of(result("g1", 0.5), result("g2", 0.33))));

            // When
            RetrievalOutcome outcome = retrieve();

            // Then: 0.8/2, 0.8/3 순으로 내려간다
            assertThat(outcome.merged().get(2).score()).isEqualTo(0.8 / 2);
            assertThat(outcome.merged().get(3).score()).isEqualTo(0.8 / 3);
            assertThat(outcome.merged().get(2).score()).isLessThan(0.8);
        }

        @Test
        @DisplayName("벡터가 빈손이면 기준값 0.01로 그래프 점수를 매긴다")
        void usesDefaultFloorWhenVectorIsEmpty() {
            // Given
            givenSearches(vectorOutcome(List.of()), graphOutcome(List.of(result("g1", 0.5))));

            // When
            RetrievalOutcome outcome = retrieve();

            // Then
            assertThat(outcome.merged()).hasSize(1);
            assertThat(outcome.merged().get(0).score()).isEqualTo(0.01 / 2);
        }
    }

    @Nested
    @DisplayName("retrieve - 경로 판정")
    class Path {

        @Test
        @DisplayName("양쪽 다 결과가 있으면 BOTH")
        void bothWhenVectorAndGraphContribute() {
            // Given
            givenSearches(
                vectorOutcome(List.of(result("v1", 0.9))),
                graphOutcome(List.of(result("g1", 0.5))));

            // When & Then
            assertThat(retrieve().path()).isEqualTo(RetrievalPath.BOTH);
        }

        @Test
        @DisplayName("그래프 결과가 중복 제거로 다 빠지면 VECTOR_ONLY")
        void vectorOnlyWhenAllGraphResultsAreDuplicates() {
            // Given
            givenSearches(
                vectorOutcome(List.of(result("dup", 0.9))),
                graphOutcome(List.of(result("dup", 0.5))));

            // When & Then
            assertThat(retrieve().path()).isEqualTo(RetrievalPath.VECTOR_ONLY);
        }

        @Test
        @DisplayName("벡터가 빈손이고 그래프만 물고 오면 GRAPH_ONLY")
        void graphOnlyWhenVectorIsEmpty() {
            // Given
            givenSearches(vectorOutcome(List.of()), graphOutcome(List.of(result("g1", 0.5))));

            // When & Then
            assertThat(retrieve().path()).isEqualTo(RetrievalPath.GRAPH_ONLY);
        }

        @Test
        @DisplayName("양쪽 다 빈손이면 NONE")
        void noneWhenNothingFound() {
            // Given
            givenSearches(vectorOutcome(List.of()), graphOutcome(List.of()));

            // When & Then
            assertThat(retrieve().path()).isEqualTo(RetrievalPath.NONE);
        }
    }

    @Nested
    @DisplayName("retrieve - 그래프 경로를 끈 상태")
    class GraphDisabled {

        @Test
        @DisplayName("그래프 검색을 아예 부르지 않고 벡터 결과만 돌려준다")
        void skipsGraphSearchEntirely() {
            // Given
            ReflectionTestUtils.setField(retrievalService, "graphEnabled", false);
            when(vectorSearchService.search(anyString(), anyLong(), any()))
                .thenReturn(vectorOutcome(List.of(result("v1", 0.9))));

            // When
            RetrievalOutcome outcome = retrieve();

            // Then
            verify(graphSearchService, never()).search(anyString());
            assertThat(outcome.merged())
                .extracting(SearchResult::documentId)
                .containsExactly("v1");
            assertThat(outcome.graph().enabled()).isFalse();
            assertThat(outcome.graphLatencyMs()).isZero();
            assertThat(outcome.path()).isEqualTo(RetrievalPath.VECTOR_ONLY);
        }
    }

    @Nested
    @DisplayName("retrieve - 그래프 조회 실패")
    class GraphFailure {

        @Test
        @DisplayName("그래프가 예외로 끝나도 벡터 결과로 계속 간다")
        void fallsBackToVectorResultsWhenGraphThrows() {
            // Given
            when(vectorSearchService.search(anyString(), anyLong(), any()))
                .thenReturn(vectorOutcome(List.of(result("v1", 0.9))));
            when(graphSearchService.search(anyString()))
                .thenThrow(new IllegalStateException("graph down"));

            // When
            RetrievalOutcome outcome = retrieve();

            // Then
            assertThat(outcome.merged())
                .extracting(SearchResult::documentId)
                .containsExactly("v1");
            assertThat(outcome.graph().results()).isEmpty();
            assertThat(outcome.path()).isEqualTo(RetrievalPath.VECTOR_ONLY);
        }
    }
}
