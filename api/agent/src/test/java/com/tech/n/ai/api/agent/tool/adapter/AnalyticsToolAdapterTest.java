package com.tech.n.ai.api.agent.tool.adapter;

import com.tech.n.ai.api.agent.config.AnalyticsConfig;
import com.tech.n.ai.api.agent.tool.dto.StatisticsDto;
import com.tech.n.ai.api.agent.tool.dto.WordFrequencyDto;
import com.tech.n.ai.domain.mongodb.service.EmergingTechAggregationService;
import com.tech.n.ai.domain.mongodb.service.GroupCountResult;
import com.tech.n.ai.domain.mongodb.service.WordFrequencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AnalyticsToolAdapter 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsToolAdapter 단위 테스트")
class AnalyticsToolAdapterTest {

    @Mock
    private EmergingTechAggregationService aggregationService;

    @Mock
    private AnalyticsConfig analyticsConfig;

    private AnalyticsToolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AnalyticsToolAdapter(aggregationService, analyticsConfig);
    }

    // ========== getStatistics 테스트 ==========

    @Nested
    @DisplayName("getStatistics")
    class GetStatistics {

        @Test
        @DisplayName("정상 집계 - 그룹별 통계 반환")
        void getStatistics_정상집계() {
            // Given
            GroupCountResult result1 = createGroupCountResult("OPENAI", 50L);
            GroupCountResult result2 = createGroupCountResult("ANTHROPIC", 30L);

            when(aggregationService.countByGroup(eq("provider"), any(), any()))
                    .thenReturn(List.of(result1, result2));

            // When
            StatisticsDto result = adapter.getStatistics("provider", "2024-01-01", "2024-01-31");

            // Then
            assertThat(result.groupBy()).isEqualTo("provider");
            assertThat(result.groups()).hasSize(2);
            assertThat(result.groups().get(0).name()).isEqualTo("OPENAI");
            assertThat(result.groups().get(0).count()).isEqualTo(50);
        }

        @Test
        @DisplayName("totalCount는 그룹 카운트 합산")
        void getStatistics_totalCount합산() {
            // Given
            GroupCountResult result1 = createGroupCountResult("A", 100L);
            GroupCountResult result2 = createGroupCountResult("B", 200L);
            GroupCountResult result3 = createGroupCountResult("C", 50L);

            when(aggregationService.countByGroup(any(), any(), any()))
                    .thenReturn(List.of(result1, result2, result3));

            // When
            StatisticsDto result = adapter.getStatistics("provider", "", "");

            // Then
            assertThat(result.totalCount()).isEqualTo(350);
        }

        @Test
        @DisplayName("빈 결과 시 totalCount 0")
        void getStatistics_빈결과() {
            // Given
            when(aggregationService.countByGroup(any(), any(), any()))
                    .thenReturn(List.of());

            // When
            StatisticsDto result = adapter.getStatistics("provider", "", "");

            // Then
            assertThat(result.totalCount()).isZero();
            assertThat(result.groups()).isEmpty();
        }

        @Test
        @DisplayName("날짜 파싱 - 빈 문자열 처리")
        void getStatistics_빈날짜() {
            // Given
            when(aggregationService.countByGroup(eq("source_type"), isNull(), isNull()))
                    .thenReturn(List.of());

            // When
            StatisticsDto result = adapter.getStatistics("source_type", "", "");

            // Then
            assertThat(result.startDate()).isEmpty();
            assertThat(result.endDate()).isEmpty();
        }

        @Test
        @DisplayName("aggregationService 예외 시 빈 통계 반환")
        void getStatistics_예외발생() {
            // Given
            when(aggregationService.countByGroup(any(), any(), any()))
                    .thenThrow(new RuntimeException("MongoDB 오류"));

            // When
            StatisticsDto result = adapter.getStatistics("provider", "", "");

            // Then
            assertThat(result.totalCount()).isZero();
            assertThat(result.groups()).isEmpty();
        }

        @Test
        @DisplayName("유효한 날짜 파싱")
        void getStatistics_유효한날짜() {
            // Given
            when(aggregationService.countByGroup(eq("update_type"), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(createGroupCountResult("MODEL_RELEASE", 10L)));

            // When
            StatisticsDto result = adapter.getStatistics("update_type", "2024-01-01", "2024-12-31");

            // Then
            assertThat(result.startDate()).isEqualTo("2024-01-01");
            assertThat(result.endDate()).isEqualTo("2024-12-31");
        }

        @Test
        @DisplayName("잘못된 날짜 형식 시 null 전달")
        void getStatistics_잘못된날짜() {
            // Given
            when(aggregationService.countByGroup(any(), isNull(), isNull()))
                    .thenReturn(List.of());

            // When
            StatisticsDto result = adapter.getStatistics("provider", "invalid-date", "also-invalid");

            // Then
            assertThat(result.groups()).isEmpty();
        }
    }

    // ========== analyzeTextFrequency 테스트 ==========

    @Nested
    @DisplayName("analyzeTextFrequency")
    class AnalyzeTextFrequency {

        @Test
        @DisplayName("정상 분석 - 단어 빈도 반환")
        void analyzeTextFrequency_정상분석() {
            // Given
            WordFrequencyResult word1 = createWordFrequencyResult("GPT", 100L);
            WordFrequencyResult word2 = createWordFrequencyResult("model", 80L);

            when(analyticsConfig.getStopWords()).thenReturn(List.of("the", "a"));
            when(aggregationService.aggregateWordFrequency(any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(word1, word2));
            when(aggregationService.countDocuments(any(), any(), any(), any(), any()))
                    .thenReturn(50L);

            // When
            WordFrequencyDto result = adapter.analyzeTextFrequency("OPENAI", "", "", "", "", 20);

            // Then
            assertThat(result.totalDocuments()).isEqualTo(50);
            assertThat(result.topWords()).hasSize(2);
            assertThat(result.topWords().get(0).word()).isEqualTo("GPT");
            assertThat(result.topWords().get(0).count()).isEqualTo(100);
        }

        @Test
        @DisplayName("불용어 필터링 (AnalyticsConfig에서 주입)")
        void analyzeTextFrequency_불용어필터링() {
            // Given
            List<String> stopWords = List.of("the", "a", "is", "are");
            when(analyticsConfig.getStopWords()).thenReturn(stopWords);
            when(aggregationService.aggregateWordFrequency(any(), any(), any(), any(), any(), eq(stopWords), anyInt()))
                    .thenReturn(List.of());
            when(aggregationService.countDocuments(any(), any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            WordFrequencyDto result = adapter.analyzeTextFrequency("", "", "", "", "", 20);

            // Then
            assertThat(result.topWords()).isEmpty();
        }

        @Test
        @DisplayName("provider 필터 적용")
        void analyzeTextFrequency_provider필터() {
            // Given
            when(analyticsConfig.getStopWords()).thenReturn(List.of());
            when(aggregationService.aggregateWordFrequency(eq("ANTHROPIC"), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(createWordFrequencyResult("Claude", 50L)));
            when(aggregationService.countDocuments(eq("ANTHROPIC"), any(), any(), any(), any()))
                    .thenReturn(25L);

            // When
            WordFrequencyDto result = adapter.analyzeTextFrequency("ANTHROPIC", "", "", "", "", 10);

            // Then
            assertThat(result.totalDocuments()).isEqualTo(25);
            assertThat(result.topWords()).hasSize(1);
        }

        @Test
        @DisplayName("소문자 필터는 대문자로 바꿔 집계 서비스에 넘긴다")
        void analyzeTextFrequency_소문자필터_대문자변환() {
            // Given
            when(analyticsConfig.getStopWords()).thenReturn(List.of());
            when(aggregationService.aggregateWordFrequency(any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of());
            when(aggregationService.countDocuments(any(), any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            adapter.analyzeTextFrequency("openai", "model_release", "rss", "", "", 20);

            // Then
            verify(aggregationService).aggregateWordFrequency(
                    eq("OPENAI"), eq("MODEL_RELEASE"), eq("RSS"), any(), any(), any(), anyInt());
            verify(aggregationService).countDocuments(
                    eq("OPENAI"), eq("MODEL_RELEASE"), eq("RSS"), any(), any());
        }

        @Test
        @DisplayName("빈 문자열·null 필터는 null로 넘겨 필터를 걸지 않는다")
        void analyzeTextFrequency_빈필터_null전달() {
            // Given
            when(analyticsConfig.getStopWords()).thenReturn(List.of());
            when(aggregationService.aggregateWordFrequency(any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of());
            when(aggregationService.countDocuments(any(), any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            adapter.analyzeTextFrequency("", null, "  ", "", "", 20);

            // Then
            verify(aggregationService).aggregateWordFrequency(
                    isNull(), isNull(), isNull(), any(), any(), any(), anyInt());
            verify(aggregationService).countDocuments(isNull(), isNull(), isNull(), any(), any());
        }

        @Test
        @DisplayName("날짜 범위 필터 적용")
        void analyzeTextFrequency_날짜필터() {
            // Given
            when(analyticsConfig.getStopWords()).thenReturn(List.of());
            when(aggregationService.aggregateWordFrequency(any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class), any(), anyInt()))
                    .thenReturn(List.of());
            when(aggregationService.countDocuments(any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(10L);

            // When
            WordFrequencyDto result = adapter.analyzeTextFrequency("", "", "", "2024-01-01", "2024-06-30", 20);

            // Then
            assertThat(result.period()).contains("2024-01-01").contains("2024-06-30");
        }

        @Test
        @DisplayName("예외 시 빈 결과 반환")
        void analyzeTextFrequency_예외발생() {
            // Given
            when(analyticsConfig.getStopWords()).thenReturn(List.of());
            when(aggregationService.aggregateWordFrequency(any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenThrow(new RuntimeException("MongoDB 오류"));

            // When
            WordFrequencyDto result = adapter.analyzeTextFrequency("", "", "", "", "", 20);

            // Then
            assertThat(result.totalDocuments()).isZero();
            assertThat(result.topWords()).isEmpty();
        }

        @Test
        @DisplayName("전체 기간 period 문자열")
        void analyzeTextFrequency_전체기간() {
            // Given
            when(analyticsConfig.getStopWords()).thenReturn(List.of());
            when(aggregationService.aggregateWordFrequency(any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of());
            when(aggregationService.countDocuments(any(), any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            WordFrequencyDto result = adapter.analyzeTextFrequency("", "", "", "", "", 20);

            // Then
            assertThat(result.period()).isEqualTo("전체 기간");
        }

        @Test
        @DisplayName("시작일만 있을 때 period 문자열")
        void analyzeTextFrequency_시작일만() {
            // Given
            when(analyticsConfig.getStopWords()).thenReturn(List.of());
            when(aggregationService.aggregateWordFrequency(any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of());
            when(aggregationService.countDocuments(any(), any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            WordFrequencyDto result = adapter.analyzeTextFrequency("", "", "", "2024-01-01", "", 20);

            // Then
            assertThat(result.period()).contains("2024-01-01").contains("~");
        }
    }

    // ========== 헬퍼 메서드 ==========

    private GroupCountResult createGroupCountResult(String id, long count) {
        GroupCountResult result = new GroupCountResult();
        result.setId(id);
        result.setCount(count);
        return result;
    }

    private WordFrequencyResult createWordFrequencyResult(String word, long count) {
        WordFrequencyResult result = new WordFrequencyResult();
        result.setId(word);
        result.setCount(count);
        return result;
    }
}
