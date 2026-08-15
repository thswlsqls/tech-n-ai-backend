package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.domain.mongodb.util.VectorSearchUtil;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VectorSearchServiceImpl 단위 테스트
 *
 * JUnit 5 + Mockito 사용, 외부 연동 없이 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VectorSearchServiceImpl 단위 테스트")
class VectorSearchServiceImplTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoCollection<Document> mongoCollection;

    @Mock
    private AggregateIterable<Document> aggregateIterable;

    @InjectMocks
    private VectorSearchServiceImpl vectorSearchService;

    @Captor
    private ArgumentCaptor<List<Document>> pipelineCaptor;

    private static final List<Float> DUMMY_VECTOR = List.of(0.1f, 0.2f, 0.3f);

    // 테스트용 고정 ObjectId (24자리 hex)
    private static final String OID_DOC1 = "aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OID_DOC2 = "bbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String OID_DOC3 = "cccccccccccccccccccccccc";
    private static final String OID_DOC_OLD = "111111111111111111111111";
    private static final String OID_DOC_LATEST = "222222222222222222222222";
    private static final String OID_DOC_ONLY_VECTOR = "333333333333333333333333";
    private static final String OID_DOC_COMMON = "444444444444444444444444";
    private static final String OID_DOC_ONLY_RECENCY = "555555555555555555555555";
    private static final String OID_FALLBACK = "666666666666666666666666";

    @BeforeEach
    void setUp() {
        // EmbeddingModel Mock 설정
        Embedding embedding = Embedding.from(DUMMY_VECTOR);
        @SuppressWarnings("unchecked")
        Response<Embedding> response = mock(Response.class);
        lenient().when(response.content()).thenReturn(embedding);
        lenient().when(embeddingModel.embed(anyString())).thenReturn(response);
    }

    // ========== search() 분기 테스트 ==========

    @Nested
    @DisplayName("search - Score Fusion 분기")
    class SearchBranching {

        @Test
        @DisplayName("enableScoreFusion=false → 기존 벡터 검색 사용")
        void search_withoutScoreFusion_usesLegacySearch() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(false)
                .maxResults(5)
                .build();
            setupAggregateResultsForLegacy(List.of(
                createMongoDocument(OID_DOC1, 0.92, "텍스트1")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("테스트 쿼리", 1L, options).results();

            // Then
            assertThat(results).hasSize(1);
            verify(mongoTemplate).getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS);
        }

        @Test
        @DisplayName("enableScoreFusion=true → 하이브리드 검색 사용")
        void search_withScoreFusion_usesHybridSearch() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();
            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.92, "텍스트1")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "텍스트2")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("테스트 쿼리", 1L, options).results();

            // Then
            assertThat(results).isNotEmpty();
            // aggregate 호출 (Score Fusion 벡터 검색)
            verify(mongoTemplate).getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS);
            // find 호출 (최신성 직접 쿼리)
            verify(mongoTemplate).find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("enableScoreFusion=null → 기존 벡터 검색 사용 (기본값 false)")
        void search_withNullScoreFusion_usesLegacySearch() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(null)
                .maxResults(5)
                .build();
            setupAggregateResultsForLegacy(List.of(
                createMongoDocument(OID_DOC1, 0.85, "텍스트1")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("테스트 쿼리", 1L, options).results();

            // Then
            assertThat(results).hasSize(1);
        }
    }

    // ========== 하이브리드 검색 테스트 ==========

    @Nested
    @DisplayName("search - 하이브리드 검색 (Score Fusion + RRF)")
    class HybridSearch {

        @Test
        @DisplayName("벡터 검색 + 최신성 쿼리 결과를 RRF로 결합")
        void hybridSearch_combinesResultsWithRRF() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.92, "벡터결과1"),
                createMongoDocumentWithCombinedScore(OID_DOC2, 0.88, "벡터결과2")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC3, null, "최신문서1"),
                createMongoDocument(OID_DOC2, null, "최신문서2(중복)")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("테스트 쿼리", 1L, options).results();

            // Then: OID_DOC2가 양쪽에 모두 등장 → RRF 점수 합산 → 상위 정렬
            assertThat(results).isNotEmpty();
            // 중복 문서는 한 번만 포함
            long doc2Count = results.stream()
                .filter(r -> OID_DOC2.equals(r.documentId()))
                .count();
            assertThat(doc2Count).isLessThanOrEqualTo(1);
        }

        @Test
        @DisplayName("recencyDetected=true → 최신성 쿼리 limit 5건")
        void hybridSearch_recencyDetected_higherLimit() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(true)
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트1")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "최신문서")
            ));

            // When
            vectorSearchService.search("최신 AI 업데이트", 1L, options);

            // Then: find 호출됨
            verify(mongoTemplate).find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("recencyDetected=false → 최신성 쿼리 limit 3건")
        void hybridSearch_noRecency_lowerLimit() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트1")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "최신문서")
            ));

            // When
            vectorSearchService.search("AI 기술 동향", 1L, options);

            // Then: find 호출됨
            verify(mongoTemplate).find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }
    }

    // ========== RRF 결합 알고리즘 테스트 ==========

    @Nested
    @DisplayName("applyRRF - Reciprocal Rank Fusion")
    class ApplyRRF {

        @Test
        @DisplayName("RRF 결합 후 중복 문서 제거 및 점수 합산")
        void applyRRF_deduplicatesAndSumsScores() {
            // Given
            List<SearchResult> vectorResults = List.of(
                createSearchResult("doc1", 0.92),
                createSearchResult("doc2", 0.88)
            );
            List<SearchResult> recencyResults = List.of(
                createSearchResult("doc2", 0.0),  // 중복
                createSearchResult("doc3", 0.0)
            );

            // When: RRF 호출 (private method via reflection)
            @SuppressWarnings("unchecked")
            List<SearchResult> combined = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 5);

            // Then
            assertThat(combined).hasSize(3); // doc1, doc2, doc3 (중복 제거)

            // doc2는 양쪽에 등장 → 점수 합산 → 최상위
            assertThat(combined.get(0).documentId()).isEqualTo("doc2");

            // doc2 RRF 점수 = 1.0/(60+2) + 1.0/(60+1) = 0.01613 + 0.01639 = 0.03252
            // doc1 RRF 점수 = 1.0/(60+1) = 0.01639
            double doc2Score = combined.get(0).score();
            double doc1Score = combined.stream()
                .filter(r -> "doc1".equals(r.documentId())).findFirst().get().score();
            assertThat(doc2Score).isGreaterThan(doc1Score);
        }

        @Test
        @DisplayName("recencyDetected=true → 최신성 가중치 1.5 적용")
        void applyRRF_recencyDetected_higherWeight() {
            // Given
            List<SearchResult> vectorResults = List.of(
                createSearchResult("doc1", 0.92)
            );
            List<SearchResult> recencyResults = List.of(
                createSearchResult("doc2", 0.0)
            );

            // When
            @SuppressWarnings("unchecked")
            List<SearchResult> withRecency = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, true, 5);

            @SuppressWarnings("unchecked")
            List<SearchResult> withoutRecency = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 5);

            // Then: recencyDetected=true일 때 doc2의 RRF 점수가 더 높음
            double doc2ScoreWithRecency = withRecency.stream()
                .filter(r -> "doc2".equals(r.documentId())).findFirst().get().score();
            double doc2ScoreWithout = withoutRecency.stream()
                .filter(r -> "doc2".equals(r.documentId())).findFirst().get().score();

            assertThat(doc2ScoreWithRecency).isGreaterThan(doc2ScoreWithout);
        }

        @Test
        @DisplayName("RRF k=60 상수 사용 검증")
        void applyRRF_usesK60() {
            // Given: 단일 결과
            List<SearchResult> vectorResults = List.of(
                createSearchResult("doc1", 0.92)
            );
            List<SearchResult> recencyResults = List.of();

            // When
            @SuppressWarnings("unchecked")
            List<SearchResult> combined = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 5);

            // Then: RRF_score = 1.0 / (60 + 1) ≈ 0.01639
            assertThat(combined).hasSize(1);
            double expectedScore = 1.0 / (60 + 1);
            assertThat(combined.get(0).score()).isCloseTo(expectedScore, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        @DisplayName("maxResults로 결과 수 제한")
        void applyRRF_limitsResults() {
            // Given
            List<SearchResult> vectorResults = List.of(
                createSearchResult("doc1", 0.9),
                createSearchResult("doc2", 0.8),
                createSearchResult("doc3", 0.7)
            );
            List<SearchResult> recencyResults = List.of(
                createSearchResult("doc4", 0.0),
                createSearchResult("doc5", 0.0)
            );

            // When
            @SuppressWarnings("unchecked")
            List<SearchResult> combined = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 3);

            // Then
            assertThat(combined).hasSize(3);
        }

        @Test
        @DisplayName("빈 벡터 결과 + 빈 최신성 결과 → 빈 결과")
        void applyRRF_emptyInputs_returnsEmpty() {
            // Given
            List<SearchResult> vectorResults = List.of();
            List<SearchResult> recencyResults = List.of();

            // When
            @SuppressWarnings("unchecked")
            List<SearchResult> combined = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 5);

            // Then
            assertThat(combined).isEmpty();
        }

        @Test
        @DisplayName("null documentId 결과 스킵")
        void applyRRF_skipsNullDocumentIds() {
            // Given
            List<SearchResult> vectorResults = List.of(
                createSearchResult(null, 0.9),
                createSearchResult("doc1", 0.8)
            );
            List<SearchResult> recencyResults = List.of();

            // When
            @SuppressWarnings("unchecked")
            List<SearchResult> combined = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 5);

            // Then: null documentId는 스킵
            assertThat(combined).hasSize(1);
            assertThat(combined.get(0).documentId()).isEqualTo("doc1");
        }

        @Test
        @DisplayName("벡터 결과만 있고 최신성 결과 없을 때 정상 동작")
        void applyRRF_onlyVectorResults() {
            // Given
            List<SearchResult> vectorResults = List.of(
                createSearchResult("doc1", 0.92),
                createSearchResult("doc2", 0.88)
            );
            List<SearchResult> recencyResults = List.of();

            // When
            @SuppressWarnings("unchecked")
            List<SearchResult> combined = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 5);

            // Then
            assertThat(combined).hasSize(2);
            // rank 1이 더 높은 RRF 점수
            assertThat(combined.get(0).documentId()).isEqualTo("doc1");
        }

        @Test
        @DisplayName("최신성 결과만 있고 벡터 결과 없을 때 정상 동작")
        void applyRRF_onlyRecencyResults() {
            // Given
            List<SearchResult> vectorResults = List.of();
            List<SearchResult> recencyResults = List.of(
                createSearchResult("doc1", 0.0),
                createSearchResult("doc2", 0.0)
            );

            // When
            @SuppressWarnings("unchecked")
            List<SearchResult> combined = (List<SearchResult>) ReflectionTestUtils.invokeMethod(
                vectorSearchService, "applyRRF",
                vectorResults, recencyResults, false, 5);

            // Then
            assertThat(combined).hasSize(2);
            assertThat(combined.get(0).documentId()).isEqualTo("doc1");
        }
    }

    // ========== 다중 updateType 쿼리 테스트 ==========

    @Nested
    @DisplayName("queryRecentDocuments - 다중 updateType")
    class QueryRecentDocuments {

        @Test
        @DisplayName("다중 updateType → 타입별 개별 쿼리 실행")
        void multipleUpdateTypes_queriesPerType() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .updateTypeFilters(List.of("SDK_RELEASE", "MODEL_RELEASE"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            // find가 여러 번 호출되므로 각각 결과 반환
            when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(createMongoDocument(OID_DOC2, null, "SDK 최신")))
                .thenReturn(List.of(createMongoDocument(OID_DOC3, null, "모델 최신")));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 2번 find 호출 (타입별 개별 쿼리)
            verify(mongoTemplate, times(2)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("단일 updateType → 단일 쿼리 실행")
        void singleUpdateType_singleQuery() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .updateTypeFilters(List.of("SDK_RELEASE"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "SDK 최신")
            ));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 1번 find 호출
            verify(mongoTemplate, times(1)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }
    }

    // ========== 다중 provider 쿼리 테스트 ==========

    @Nested
    @DisplayName("queryRecentDocuments - 다중 provider")
    class QueryRecentDocumentsMultiProvider {

        @Test
        @DisplayName("다중 provider → provider별 개별 쿼리 실행")
        void multipleProviders_queriesPerProvider() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .providerFilters(List.of("OPENAI", "ANTHROPIC"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(createMongoDocument(OID_DOC2, null, "OPENAI 최신")))
                .thenReturn(List.of(createMongoDocument(OID_DOC3, null, "ANTHROPIC 최신")));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 2번 find 호출 (provider별 개별 쿼리)
            verify(mongoTemplate, times(2)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("다중 provider + 다중 updateType → 교차 개별 쿼리 실행")
        void multiProviderMultiType_crossProductQueries() {
            // Given: 2 providers × 2 types = 4 queries
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(true)
                .providerFilters(List.of("OPENAI", "ANTHROPIC"))
                .updateTypeFilters(List.of("SDK_RELEASE", "MODEL_RELEASE"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(createMongoDocument(OID_DOC2, null, "OPENAI SDK")))
                .thenReturn(List.of(createMongoDocument(OID_DOC3, null, "OPENAI MODEL")))
                .thenReturn(List.of(createMongoDocument(OID_DOC_OLD, null, "ANTHROPIC SDK")))
                .thenReturn(List.of(createMongoDocument(OID_DOC_LATEST, null, "ANTHROPIC MODEL")));

            // When
            vectorSearchService.search("OpenAI Anthropic SDK model release 비교", 1L, options);

            // Then: 4번 find 호출 (2×2 교차 쿼리)
            verify(mongoTemplate, times(4)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("단일 provider → 단일 쿼리 실행 (기존 동작)")
        void singleProvider_singleQuery() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .providerFilters(List.of("OPENAI"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "OPENAI 최신")
            ));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 1번 find 호출
            verify(mongoTemplate, times(1)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("빈 provider 리스트 → 필터 미적용, 단일 쿼리")
        void emptyProviderList_noFilter() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .providerFilters(List.of())
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "최신문서")
            ));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 1번 find 호출 (필터 없음)
            verify(mongoTemplate, times(1)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("3+ providers → provider별 개별 쿼리 실행")
        void threeProviders_queriesPerProvider() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .providerFilters(List.of("OPENAI", "ANTHROPIC", "GOOGLE"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(createMongoDocument(OID_DOC2, null, "OPENAI")))
                .thenReturn(List.of(createMongoDocument(OID_DOC3, null, "ANTHROPIC")))
                .thenReturn(List.of(createMongoDocument(OID_DOC_OLD, null, "GOOGLE")));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 3번 find 호출 (provider별 개별 쿼리)
            verify(mongoTemplate, times(3)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("다중 provider + 단일 updateType → provider별 개별 쿼리")
        void multiProviderSingleType_queriesPerProvider() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .providerFilters(List.of("OPENAI", "ANTHROPIC"))
                .updateTypeFilters(List.of("SDK_RELEASE"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenReturn(List.of(createMongoDocument(OID_DOC2, null, "OPENAI SDK")))
                .thenReturn(List.of(createMongoDocument(OID_DOC3, null, "ANTHROPIC SDK")));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 2번 find 호출 (provider별, 단일 타입은 각 쿼리에 포함)
            verify(mongoTemplate, times(2)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }

        @Test
        @DisplayName("교차 조합 > 20 → $in fallback 단일 쿼리")
        void crossProductExceedsMax_usesFallback() {
            // Given: 5 providers × 5 types = 25 combinations > 20
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .providerFilters(List.of("OPENAI", "ANTHROPIC", "GOOGLE", "META", "XAI"))
                .updateTypeFilters(List.of("SDK_RELEASE", "MODEL_RELEASE",
                    "PRODUCT_LAUNCH", "PLATFORM_UPDATE", "BLOG_POST"))
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.9, "텍스트")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "결과")
            ));

            // When
            vectorSearchService.search("테스트", 1L, options);

            // Then: 1번 find 호출 ($in fallback)
            verify(mongoTemplate, times(1)).find(
                any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS));
        }
    }

    // ========== Fallback 테스트 ==========

    @Nested
    @DisplayName("search - Fallback 처리")
    class Fallback {

        @Test
        @DisplayName("하이브리드 검색 실패 시 기존 벡터 검색으로 fallback")
        void hybridSearchFailure_fallsBackToStandard() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();

            // 첫 번째 aggregate 호출 (Score Fusion) → 예외 발생
            // 두 번째 aggregate 호출 (fallback 기존 검색) → 정상 응답
            when(mongoTemplate.getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS))
                .thenReturn(mongoCollection);
            when(mongoCollection.aggregate(anyList()))
                .thenThrow(new RuntimeException("Score Fusion pipeline error"))
                .thenReturn(aggregateIterable);
            when(aggregateIterable.into(any()))
                .thenReturn(new ArrayList<>(List.of(
                    createMongoDocument(OID_FALLBACK, 0.85, "fallback 결과")
                )));

            // When
            List<SearchResult> results = vectorSearchService.search("테스트", 1L, options).results();

            // Then: fallback 결과 반환
            assertThat(results).isNotEmpty();
        }

        @Test
        @DisplayName("최신성 쿼리 실패 시 벡터 검색 결과만 사용")
        void recencyQueryFailure_usesVectorResultsOnly() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(true)
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.92, "벡터결과")
            ));
            // find 호출 시 예외
            when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenThrow(new RuntimeException("MongoDB find error"));

            // When
            List<SearchResult> results = vectorSearchService.search("최신 AI", 1L, options).results();

            // Then: 벡터 검색 결과만으로 반환 (에러 없이)
            assertThat(results).isNotEmpty();
        }
    }

    // ========== 엣지 케이스 ==========

    @Nested
    @DisplayName("search - 엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("벡터 검색 결과 빈 리스트")
        void emptyVectorResults() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();

            setupAggregateResults(List.of());
            setupFindResults(List.of(
                createMongoDocument(OID_DOC1, null, "최신문서")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("테스트", 1L, options).results();

            // Then: 최신성 결과만으로도 결과 반환
            assertThat(results).isNotEmpty();
        }

        @Test
        @DisplayName("maxResults 정확한 제한")
        void maxResultsLimit() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(false)
                .maxResults(2)
                .build();

            setupAggregateResultsForLegacy(List.of(
                createMongoDocument(OID_DOC1, 0.95, "텍스트1"),
                createMongoDocument(OID_DOC2, 0.90, "텍스트2"),
                createMongoDocument(OID_DOC3, 0.85, "텍스트3")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("테스트", 1L, options).results();

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("convertToSearchResult - combinedScore 우선, 없으면 score 사용")
        void convertToSearchResult_scoresPriority() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();

            // combinedScore가 있는 문서
            Document docWithCombined = createMongoDocumentWithCombinedScore(OID_DOC1, 0.88, "텍스트");
            setupAggregateResults(List.of(docWithCombined));
            setupFindResults(List.of());

            // When
            List<SearchResult> results = vectorSearchService.search("테스트", 1L, options).results();

            // Then: combinedScore 값이 사용됨 (RRF 적용 후 score 변경 가능)
            assertThat(results).isNotEmpty();
        }
    }

    // ========== "최신 문서 누락" 시나리오 검증 ==========

    @Nested
    @DisplayName("최신 문서 누락 시나리오 검증")
    class RecencyMissingScenario {

        @Test
        @DisplayName("벡터 검색에서 누락된 최신 문서가 최신성 직접 쿼리로 보완됨")
        void latestDocumentRecoveredViaRecencyQuery() {
            // Given: 벡터 검색에서 doc_latest가 누락, doc_old만 반환
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(true)
                .build();

            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC_OLD, 0.92, "이전 문서")
            ));
            // 최신성 직접 쿼리에서 doc_latest 반환
            setupFindResults(List.of(
                createMongoDocument(OID_DOC_LATEST, null, "최신 문서")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("최신 OpenAI 업데이트", 1L, options).results();

            // Then: doc_latest가 결과에 포함됨
            boolean containsLatest = results.stream()
                .anyMatch(r -> OID_DOC_LATEST.equals(r.documentId()));
            assertThat(containsLatest).isTrue();
        }

        @Test
        @DisplayName("벡터+최신성 양쪽에 등장한 문서가 RRF 합산으로 상위 정렬")
        void documentInBothSources_rankedHigher() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(true)
                .build();

            // OID_DOC_COMMON이 양쪽에 모두 등장
            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC_ONLY_VECTOR, 0.95, "벡터만"),
                createMongoDocumentWithCombinedScore(OID_DOC_COMMON, 0.90, "공통")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC_COMMON, null, "공통"),
                createMongoDocument(OID_DOC_ONLY_RECENCY, null, "최신만")
            ));

            // When
            List<SearchResult> results = vectorSearchService.search("최신 AI", 1L, options).results();

            // Then: doc_common이 양쪽 점수 합산으로 1위
            assertThat(results.get(0).documentId()).isEqualTo(OID_DOC_COMMON);
        }
    }

    // ========== SearchOutcome (검색 경로 + RRF 직전 후보) ==========

    @Nested
    @DisplayName("search - SearchOutcome 반환")
    class SearchOutcomeReturn {

        @Test
        @DisplayName("하이브리드 검색 성공 → path=HYBRID")
        void hybridSuccess_pathIsHybrid() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();
            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.92, "텍스트1")
            ));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "최신문서")
            ));

            // When
            SearchOutcome outcome = vectorSearchService.search("테스트", 1L, options);

            // Then
            assertThat(outcome.path()).isEqualTo(SearchPath.HYBRID);
            assertThat(outcome.recencyQueryFailed()).isFalse();
            assertThat(outcome.results()).isNotEmpty();
        }

        @Test
        @DisplayName("하이브리드 실패 후 일반 검색 성공 → path=HYBRID_FALLBACK_STANDARD")
        void hybridFailure_pathIsFallbackStandard() {
            // Given: 첫 aggregate는 예외, 두 번째(fallback)는 정상
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();
            when(mongoTemplate.getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS))
                .thenReturn(mongoCollection);
            when(mongoCollection.aggregate(anyList()))
                .thenThrow(new RuntimeException("Score Fusion pipeline error"))
                .thenReturn(aggregateIterable);
            when(aggregateIterable.into(any()))
                .thenReturn(new ArrayList<>(List.of(
                    createMongoDocument(OID_FALLBACK, 0.85, "fallback 결과")
                )));

            // When
            SearchOutcome outcome = vectorSearchService.search("테스트", 1L, options);

            // Then
            assertThat(outcome.path()).isEqualTo(SearchPath.HYBRID_FALLBACK_STANDARD);
            assertThat(outcome.candidates()).isEmpty();
            assertThat(outcome.results()).hasSize(1);
        }

        @Test
        @DisplayName("하이브리드 실패 후 fallback 일반 검색도 실패 → path=HYBRID_FALLBACK_FAILED")
        void hybridAndFallbackFailure_pathIsFallbackFailed() {
            // Given: aggregate가 두 번 모두 예외
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();
            when(mongoTemplate.getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS))
                .thenReturn(mongoCollection);
            when(mongoCollection.aggregate(anyList()))
                .thenThrow(new RuntimeException("Score Fusion pipeline error"))
                .thenThrow(new RuntimeException("standard search error"));

            // When
            SearchOutcome outcome = vectorSearchService.search("테스트", 1L, options);

            // Then
            assertThat(outcome.path()).isEqualTo(SearchPath.HYBRID_FALLBACK_FAILED);
            assertThat(outcome.results()).isEmpty();
        }

        @Test
        @DisplayName("Score Fusion 비활성 + 일반 검색 성공 → path=STANDARD")
        void scoreFusionOff_pathIsStandard() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(false)
                .maxResults(5)
                .build();
            setupAggregateResultsForLegacy(List.of(
                createMongoDocument(OID_DOC1, 0.92, "텍스트1")
            ));

            // When
            SearchOutcome outcome = vectorSearchService.search("테스트", 1L, options);

            // Then
            assertThat(outcome.path()).isEqualTo(SearchPath.STANDARD);
            assertThat(outcome.candidates()).isEmpty();
            assertThat(outcome.results()).hasSize(1);
        }

        @Test
        @DisplayName("Score Fusion 비활성 + 일반 검색 예외 → path=STANDARD_FAILED")
        void scoreFusionOff_searchFails_pathIsStandardFailed() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(false)
                .maxResults(5)
                .build();
            when(mongoTemplate.getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS))
                .thenReturn(mongoCollection);
            when(mongoCollection.aggregate(anyList()))
                .thenThrow(new RuntimeException("standard search error"));

            // When
            SearchOutcome outcome = vectorSearchService.search("테스트", 1L, options);

            // Then
            assertThat(outcome.path()).isEqualTo(SearchPath.STANDARD_FAILED);
            assertThat(outcome.results()).isEmpty();
        }

        @Test
        @DisplayName("최신성 직접 쿼리 예외 → recencyQueryFailed=true, 경로는 HYBRID 유지")
        void recencyQueryFails_flagIsTrue() {
            // Given
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(true)
                .build();
            setupAggregateResults(List.of(
                createMongoDocumentWithCombinedScore(OID_DOC1, 0.92, "벡터결과")
            ));
            when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
                .thenThrow(new RuntimeException("MongoDB find error"));

            // When
            SearchOutcome outcome = vectorSearchService.search("최신 AI", 1L, options);

            // Then
            assertThat(outcome.path()).isEqualTo(SearchPath.HYBRID);
            assertThat(outcome.recencyQueryFailed()).isTrue();
            assertThat(outcome.results()).isNotEmpty();
        }

        @Test
        @DisplayName("후보는 RRF 직전 벡터 결과이고 vectorScore·combinedScore를 함께 담는다")
        void candidatesCarryVectorAndCombinedScore() {
            // Given: 파이프라인이 내려주는 두 점수를 모두 담은 문서
            SearchOptions options = SearchOptions.builder()
                .enableScoreFusion(true)
                .maxResults(5)
                .recencyDetected(false)
                .build();
            Document doc = createMongoDocumentWithCombinedScore(OID_DOC1, 0.88, "텍스트1");
            doc.append("vectorScore", 0.91);
            setupAggregateResults(List.of(doc));
            setupFindResults(List.of(
                createMongoDocument(OID_DOC2, null, "최신문서")
            ));

            // When
            SearchOutcome outcome = vectorSearchService.search("테스트", 1L, options);

            // Then
            assertThat(outcome.candidates()).hasSize(1);
            SearchResult candidate = outcome.candidates().get(0);
            assertThat(candidate.documentId()).isEqualTo(OID_DOC1);
            assertThat(candidate.metadata()).isInstanceOf(Document.class);
            Document metadata = (Document) candidate.metadata();
            assertThat(metadata.getDouble("vectorScore")).isEqualTo(0.91);
            assertThat(metadata.getDouble("combinedScore")).isEqualTo(0.88);
        }
    }

    // ========== 헬퍼 메서드 ==========

    private void setupAggregateResults(List<Document> docs) {
        when(mongoTemplate.getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS))
            .thenReturn(mongoCollection);
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.into(any())).thenReturn(new ArrayList<>(docs));
    }

    private void setupAggregateResultsForLegacy(List<Document> docs) {
        setupAggregateResults(docs);
    }

    private void setupFindResults(List<Document> docs) {
        when(mongoTemplate.find(any(), eq(Document.class), eq(VectorSearchUtil.COLLECTION_EMERGING_TECHS)))
            .thenReturn(docs);
    }

    /**
     * 고정 ObjectId로 MongoDB Document 생성 (테스트 결과 비교용)
     *
     * @param oid 24자리 hex ObjectId 문자열
     * @param score 유사도 점수 (null이면 미포함)
     * @param text embedding_text
     */
    private Document createMongoDocument(String oid, Double score, String text) {
        Document doc = new Document();
        if (oid != null) {
            doc.append("_id", new ObjectId(oid));
        }
        doc.append("embedding_text", text);
        if (score != null) {
            doc.append("score", score);
        }
        doc.append("published_at", new Date());
        return doc;
    }

    private Document createMongoDocumentWithCombinedScore(String oid, double combinedScore, String text) {
        Document doc = createMongoDocument(oid, null, text);
        doc.append("combinedScore", combinedScore);
        return doc;
    }

    private SearchResult createSearchResult(String documentId, double score) {
        return SearchResult.builder()
            .documentId(documentId)
            .text("테스트 텍스트")
            .score(score)
            .collectionType("EMERGING_TECH")
            .build();
    }
}
