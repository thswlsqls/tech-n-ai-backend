package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.AugmentOutcome;
import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
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
        @DisplayName("그래프 검색이 꺼짐 결과를 돌려주면 벡터 결과만 남는다")
        void keepsVectorResultsWhenGraphIsDisabled() {
            // Given
            givenSearches(
                vectorOutcome(List.of(result("v1", 0.9))),
                GraphSearchOutcome.disabled());

            // When
            RetrievalOutcome outcome = retrieve();

            // Then
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

    @Nested
    @DisplayName("retrieve - 근거가 약할 때 조건을 완화해 다시 찾기")
    class Augment {

        @BeforeEach
        void enableAugment() {
            ReflectionTestUtils.setField(retrievalService, "augmentEnabled", true);
            ReflectionTestUtils.setField(retrievalService, "augmentMaxAttempts", 2);
            ReflectionTestUtils.setField(retrievalService, "augmentMinVectorScore", 0.72);
            ReflectionTestUtils.setField(retrievalService, "augmentRelaxedMinScore", 0.5);
        }

        /** metadata에 vectorScore를 실은 후보. 약함 판정은 score()가 아니라 이 값을 본다 */
        private SearchResult scored(String documentId, double vectorScore) {
            return scored(documentId, vectorScore, 0.99);
        }

        /** 최종 점수까지 지정한 후보. 재점수화가 무엇을 기준으로 도는지 볼 때 쓴다 */
        private SearchResult scored(String documentId, double vectorScore, double score) {
            return SearchResult.builder()
                .documentId(documentId)
                .text(documentId + " 본문")
                .score(score)
                .collectionType("EMERGING_TECH")
                .metadata(new Document("vectorScore", vectorScore))
                .build();
        }

        private void givenVectorSearches(SearchOutcome first, SearchOutcome... retries) {
            when(vectorSearchService.search(anyString(), anyLong(), any())).thenReturn(first, retries);
            when(graphSearchService.search(anyString())).thenReturn(graphOutcome(List.of()));
        }

        /** provider·updateType 필터가 걸려 있어 완화 사다리 두 단계가 모두 살아 있는 옵션 */
        private SearchOptions filteredOptions() {
            return filteredOptions(5);
        }

        /** 최종 결과 개수 상한까지 지정한 옵션. 병합이 몇 건까지 붙이는지 볼 때 쓴다 */
        private SearchOptions filteredOptions(int maxResults) {
            return SearchOptions.builder()
                .maxResults(maxResults)
                .minSimilarityScore(0.7)
                .providerFilters(List.of("OPENAI"))
                .updateTypeFilters(List.of("MODEL"))
                .build();
        }

        private RetrievalOutcome retrieveWith(SearchOptions options) {
            return retrievalService.retrieve("질문", 0L, options);
        }

        /**
         * 필터를 푼 재검색이라 1차보다 후보를 많이 물고 온 결과
         *
         * 합친 후보 목록은 두 검색 중 긴 쪽 길이로 자르니, 재검색이 1차보다 후보를 많이 가져와야
         * 새 후보가 목록에 남아 약함 판정을 벗어난다.
         */
        private SearchOutcome strongRetry() {
            return vectorOutcome(List.of(scored("v2", 0.90), scored("v3", 0.75)));
        }

        @Test
        @DisplayName("보강을 꺼두면 근거가 약해도 다시 찾지 않고 1차 결과를 그대로 돌려준다")
        void disabledKeepsFirstResult() {
            // Given: 후보 최고 vectorScore가 문턱에 못 미치지만 스위치가 꺼져 있다
            ReflectionTestUtils.setField(retrievalService, "augmentEnabled", false);
            SearchOutcome first = vectorOutcome(List.of(scored("v1", 0.60)));
            givenVectorSearches(first);

            // When
            RetrievalOutcome outcome = retrieveWith(filteredOptions());

            // Then
            verify(vectorSearchService, times(1)).search(anyString(), anyLong(), any());
            assertThat(outcome.vector()).isSameAs(first);
            assertThat(outcome.augment()).isEqualTo(AugmentOutcome.none());
        }

        @Test
        @DisplayName("후보가 한 건도 없으면 조건을 완화해 다시 찾는다")
        void retriesWhenCandidatesAreEmpty() {
            // Given
            SearchOutcome first = vectorOutcome(List.of());
            SearchOutcome retry = vectorOutcome(List.of(scored("v1", 0.90)));
            givenVectorSearches(first, retry);

            // When
            RetrievalOutcome outcome = retrieveWith(filteredOptions());

            // Then
            assertThat(outcome.augment().triggered()).isTrue();
            assertThat(outcome.augment().attempts()).isEqualTo(1);
            assertThat(outcome.vector().results())
                .extracting(SearchResult::documentId)
                .containsExactly("v1");
        }

        @Test
        @DisplayName("후보 최고 vectorScore가 문턱에 못 미치면 조건을 완화해 다시 찾는다")
        void retriesWhenTopVectorScoreIsBelowThreshold() {
            // Given: 0.60은 문턱 0.72보다 낮다
            givenVectorSearches(vectorOutcome(List.of(scored("v1", 0.60))), strongRetry());

            // When
            RetrievalOutcome outcome = retrieveWith(filteredOptions());

            // Then
            assertThat(outcome.augment().triggered()).isTrue();
            assertThat(outcome.augment().attempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("후보 최고 vectorScore가 문턱을 넘으면 다시 찾지 않는다")
        void skipsRetryWhenTopVectorScoreIsHighEnough() {
            // Given: 0.80은 문턱 0.72를 넘는다
            givenVectorSearches(vectorOutcome(List.of(scored("v1", 0.80))));

            // When
            RetrievalOutcome outcome = retrieveWith(filteredOptions());

            // Then
            verify(vectorSearchService, times(1)).search(anyString(), anyLong(), any());
            assertThat(outcome.augment().triggered()).isFalse();
            assertThat(outcome.augment().attempts()).isZero();
        }

        @Test
        @DisplayName("첫 완화 단계는 provider·updateType 필터만 떼고 나머지 조건은 그대로 둔다")
        void firstRelaxationDropsCategoryFiltersOnly() {
            // Given
            givenVectorSearches(vectorOutcome(List.of(scored("v1", 0.60))), strongRetry());

            // When
            retrieveWith(filteredOptions());

            // Then
            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(vectorSearchService, times(2)).search(anyString(), anyLong(), captor.capture());
            SearchOptions relaxed = captor.getAllValues().get(1);
            assertThat(relaxed.providerFilters()).isNull();
            assertThat(relaxed.updateTypeFilters()).isNull();
            assertThat(relaxed.minSimilarityScore()).isEqualTo(0.7);
            assertThat(relaxed.maxResults()).isEqualTo(5);
        }

        @Test
        @DisplayName("상한에 걸리면 완화 단계가 남아 있어도 그만 돌고 그때까지 모은 결과로 끝낸다")
        void stopsAtMaxAttempts() {
            // Given: 완화 사다리는 두 단계인데 상한이 1이다
            ReflectionTestUtils.setField(retrievalService, "augmentMaxAttempts", 1);
            givenVectorSearches(
                vectorOutcome(List.of(scored("v1", 0.60))),
                vectorOutcome(List.of(scored("v2", 0.65))));

            // When
            RetrievalOutcome outcome = retrieveWith(filteredOptions());

            // Then: 여전히 약하지만 상한이라 멈추고, 1차 결과 뒤에 재검색 결과를 붙인 채로 끝낸다
            verify(vectorSearchService, times(2)).search(anyString(), anyLong(), any());
            assertThat(outcome.augment().attempts()).isEqualTo(1);
            assertThat(outcome.vector().results())
                .extracting(SearchResult::documentId)
                .containsExactly("v1", "v2");
        }

        @Test
        @DisplayName("완화할 조건이 없으면 같은 검색을 반복하지 않는다")
        void skipsRetryWhenNothingToRelax() {
            // Given: 필터가 없고 유사도 문턱도 이미 완화값보다 낮다
            SearchOutcome first = vectorOutcome(List.of(scored("v1", 0.60)));
            givenVectorSearches(first);

            // When
            RetrievalOutcome outcome = retrieveWith(
                SearchOptions.builder().minSimilarityScore(0.4).build());

            // Then
            verify(vectorSearchService, times(1)).search(anyString(), anyLong(), any());
            assertThat(outcome.augment().triggered()).isTrue();
            assertThat(outcome.augment().attempts()).isZero();
            assertThat(outcome.augment().adopted()).isFalse();
        }

        @Test
        @DisplayName("다시 찾아 근거가 충분해지면 남은 완화 단계는 돌지 않는다")
        void stopsOnceResultIsStrongEnough() {
            // Given: 첫 재검색이 문턱을 넘는 후보를 물고 온다
            givenVectorSearches(vectorOutcome(List.of(scored("v1", 0.60))), strongRetry());

            // When
            RetrievalOutcome outcome = retrieveWith(filteredOptions());

            // Then: 사다리 2단 중 1단만 돌았다
            verify(vectorSearchService, times(2)).search(anyString(), anyLong(), any());
            assertThat(outcome.augment().attempts()).isEqualTo(1);
        }

        @Nested
        @DisplayName("1차 결과 뒤에 재검색 결과 붙이기")
        class MergeRetryResults {

            @BeforeEach
            void allowSingleRetry() {
                ReflectionTestUtils.setField(retrievalService, "augmentMaxAttempts", 1);
            }

            /** 재검색을 부르지만 문턱은 계속 못 넘는 1차 결과. 최저점은 0.8이다 */
            private SearchOutcome weakFirst() {
                return vectorOutcome(List.of(scored("v1", 0.60, 0.9), scored("v2", 0.60, 0.8)));
            }

            @Test
            @DisplayName("1차 결과는 순서와 점수 그대로 앞자리를 지킨다")
            void keepsFirstResultsUntouchedAtTheFront() {
                // Given
                givenVectorSearches(
                    weakFirst(),
                    vectorOutcome(List.of(scored("r1", 0.65, 0.99))));

                // When
                RetrievalOutcome outcome = retrieveWith(filteredOptions());

                // Then
                List<SearchResult> results = outcome.vector().results();
                assertThat(results.get(0).documentId()).isEqualTo("v1");
                assertThat(results.get(0).score()).isEqualTo(0.9);
                assertThat(results.get(1).documentId()).isEqualTo("v2");
                assertThat(results.get(1).score()).isEqualTo(0.8);
            }

            @Test
            @DisplayName("재검색이 새로 물어온 문서를 1차 결과 뒤에 붙인다")
            void appendsNewRetryDocumentsAfterFirstResults() {
                // Given
                givenVectorSearches(
                    weakFirst(),
                    vectorOutcome(List.of(scored("r1", 0.65, 0.99), scored("r2", 0.65, 0.98))));

                // When
                RetrievalOutcome outcome = retrieveWith(filteredOptions());

                // Then
                assertThat(outcome.vector().results())
                    .extracting(SearchResult::documentId)
                    .containsExactly("v1", "v2", "r1", "r2");
            }

            @Test
            @DisplayName("붙이는 재검색 문서 점수를 1차 최저점 아래로 다시 매긴다")
            void rescoresRetryDocumentsBelowFirstFloor() {
                // Given: 1차 최저점이 0.8이고 재검색 문서는 그보다 높은 0.99·0.98을 달고 온다
                givenVectorSearches(
                    weakFirst(),
                    vectorOutcome(List.of(scored("r1", 0.65, 0.99), scored("r2", 0.65, 0.98))));

                // When
                RetrievalOutcome outcome = retrieveWith(filteredOptions());

                // Then: 0.8/2, 0.8/3 순으로 내려가 1차 최저점을 넘지 못한다
                List<SearchResult> results = outcome.vector().results();
                assertThat(results.get(2).score()).isEqualTo(0.8 / 2);
                assertThat(results.get(3).score()).isEqualTo(0.8 / 3);
                assertThat(results.get(2).score()).isLessThan(0.8);
            }

            @Test
            @DisplayName("1차가 이미 물고 온 문서는 재검색 쪽에서 뺀다")
            void dropsRetryDocumentsAlreadyFoundByFirstSearch() {
                // Given
                givenVectorSearches(
                    vectorOutcome(List.of(scored("v1", 0.60, 0.9), scored("dup", 0.60, 0.8))),
                    vectorOutcome(List.of(scored("dup", 0.65, 0.99), scored("r1", 0.65, 0.98))));

                // When
                RetrievalOutcome outcome = retrieveWith(filteredOptions());

                // Then
                assertThat(outcome.vector().results())
                    .extracting(SearchResult::documentId)
                    .containsExactly("v1", "dup", "r1");
            }

            @Test
            @DisplayName("합친 결과가 maxResults를 넘지 않는다")
            void capsMergedResultsAtMaxResults() {
                // Given: 1차 2건에 재검색 3건인데 상한이 3이다
                givenVectorSearches(
                    weakFirst(),
                    vectorOutcome(List.of(
                        scored("r1", 0.65, 0.99), scored("r2", 0.65, 0.98), scored("r3", 0.65, 0.97))));

                // When
                RetrievalOutcome outcome = retrieveWith(filteredOptions(3));

                // Then
                assertThat(outcome.vector().results())
                    .extracting(SearchResult::documentId)
                    .containsExactly("v1", "v2", "r1");
            }

            @Test
            @DisplayName("1차 결과가 이미 maxResults를 채웠으면 재검색 문서는 최종 목록에 들어가지 못한다 - 보강의 한계다")
            void addsNothingWhenFirstResultsAlreadyFillMaxResults() {
                // Given: 1차 2건으로 상한 2를 이미 채웠다
                givenVectorSearches(
                    weakFirst(),
                    vectorOutcome(List.of(scored("r1", 0.65, 0.99))));

                // When
                RetrievalOutcome outcome = retrieveWith(filteredOptions(2));

                // Then: 재검색은 돌았지만 붙일 자리가 없다
                assertThat(outcome.augment().attempts()).isEqualTo(1);
                assertThat(outcome.vector().results())
                    .extracting(SearchResult::documentId)
                    .containsExactly("v1", "v2");
                assertThat(outcome.augment().adopted()).isFalse();
            }

            @Test
            @DisplayName("재검색 문서가 최종 목록에 들어가면 보강했다고 남긴다")
            void marksAdoptedWhenRetryDocumentEntersFinalResults() {
                // Given
                givenVectorSearches(
                    weakFirst(),
                    vectorOutcome(List.of(scored("r1", 0.65, 0.99))));

                // When
                RetrievalOutcome outcome = retrieveWith(filteredOptions());

                // Then
                assertThat(outcome.vector().results()).hasSize(3);
                assertThat(outcome.augment().adopted()).isTrue();
            }
        }
    }
}
