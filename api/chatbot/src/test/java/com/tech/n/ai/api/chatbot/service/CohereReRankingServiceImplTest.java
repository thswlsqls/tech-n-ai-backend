package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CohereReRankingServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CohereReRankingServiceImpl 단위 테스트")
class CohereReRankingServiceImplTest {

    @Mock
    private ScoringModel scoringModel;

    private CohereReRankingServiceImpl reRankingService;

    @BeforeEach
    void setUp() {
        reRankingService = new CohereReRankingServiceImpl();
        ReflectionTestUtils.setField(reRankingService, "enabled", true);
        ReflectionTestUtils.setField(reRankingService, "minScore", 0.3);
        ReflectionTestUtils.setField(reRankingService, "scoringModel", scoringModel);
    }

    private List<SearchResult> createTestResults() {
        return List.of(
            SearchResult.builder().documentId("1").text("문서1 내용").score(0.8).collectionType("EMERGING_TECH").build(),
            SearchResult.builder().documentId("2").text("문서2 내용").score(0.7).collectionType("EMERGING_TECH").build(),
            SearchResult.builder().documentId("3").text("문서3 내용").score(0.6).collectionType("EMERGING_TECH").build()
        );
    }

    // ========== rerank 테스트 ==========

    @Nested
    @DisplayName("rerank")
    class Rerank {

        @Test
        @DisplayName("정상 re-ranking - 점수 기반 정렬")
        @SuppressWarnings("unchecked")
        void rerank_정상() {
            // Given
            List<SearchResult> documents = createTestResults();
            Response<List<Double>> response = new Response<>(List.of(0.5, 0.9, 0.2));
            when(scoringModel.scoreAll(any(), anyString())).thenReturn(response);

            // When
            List<SearchResult> result = reRankingService.rerank("쿼리", documents, 3);

            // Then
            assertThat(result).hasSize(2); // 0.2는 minScore(0.3) 미만이라 제외
            assertThat(result.get(0).score()).isEqualTo(0.9);
            assertThat(result.get(1).score()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("minScore 미만 결과 필터링")
        @SuppressWarnings("unchecked")
        void rerank_minScore필터링() {
            // Given
            List<SearchResult> documents = createTestResults();
            Response<List<Double>> response = new Response<>(List.of(0.1, 0.2, 0.1));
            when(scoringModel.scoreAll(any(), anyString())).thenReturn(response);

            // When
            List<SearchResult> result = reRankingService.rerank("쿼리", documents, 3);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("topK 제한 적용")
        @SuppressWarnings("unchecked")
        void rerank_topK제한() {
            // Given
            List<SearchResult> documents = createTestResults();
            Response<List<Double>> response = new Response<>(List.of(0.8, 0.7, 0.6));
            when(scoringModel.scoreAll(any(), anyString())).thenReturn(response);

            // When
            List<SearchResult> result = reRankingService.rerank("쿼리", documents, 2);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("비활성화 시 fallback 정렬")
        void rerank_비활성화() {
            // Given
            ReflectionTestUtils.setField(reRankingService, "enabled", false);
            List<SearchResult> documents = createTestResults();

            // When
            List<SearchResult> result = reRankingService.rerank("쿼리", documents, 3);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).score()).isEqualTo(0.8); // 원래 점수 기준 정렬
        }

        @Test
        @DisplayName("scoringModel null일 때 fallback")
        void rerank_scoringModelNull() {
            // Given
            ReflectionTestUtils.setField(reRankingService, "scoringModel", null);
            List<SearchResult> documents = createTestResults();

            // When
            List<SearchResult> result = reRankingService.rerank("쿼리", documents, 3);

            // Then
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("빈 문서 리스트 시 fallback")
        void rerank_빈리스트() {
            // When
            List<SearchResult> result = reRankingService.rerank("쿼리", List.of(), 3);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("scoringModel 예외 시 fallback")
        void rerank_예외() {
            // Given
            List<SearchResult> documents = createTestResults();
            when(scoringModel.scoreAll(any(), anyString()))
                .thenThrow(new RuntimeException("Cohere API 에러"));

            // When
            List<SearchResult> result = reRankingService.rerank("쿼리", documents, 3);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).score()).isEqualTo(0.8);
        }
    }

    // ========== isEnabled 테스트 ==========

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        @Test
        @DisplayName("enabled=true, scoringModel!=null → true")
        void isEnabled_true() {
            assertThat(reRankingService.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("enabled=false → false")
        void isEnabled_disabled() {
            ReflectionTestUtils.setField(reRankingService, "enabled", false);
            assertThat(reRankingService.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("scoringModel=null → false")
        void isEnabled_modelNull() {
            ReflectionTestUtils.setField(reRankingService, "scoringModel", null);
            assertThat(reRankingService.isEnabled()).isFalse();
        }
    }
}
