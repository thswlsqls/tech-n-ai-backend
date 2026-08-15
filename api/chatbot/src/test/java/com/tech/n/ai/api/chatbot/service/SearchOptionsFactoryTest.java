package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.SearchContext;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchOptionsFactory 단위 테스트
 */
@DisplayName("SearchOptionsFactory 단위 테스트")
class SearchOptionsFactoryTest {

    private static final int MAX_SEARCH_RESULTS = 5;
    private static final double MIN_SIMILARITY_SCORE = 0.7;
    private static final int RECENCY_MONTHS = 6;

    private SearchOptionsFactory searchOptionsFactory;

    @BeforeEach
    void setUp() {
        searchOptionsFactory = new SearchOptionsFactory();
        ReflectionTestUtils.setField(searchOptionsFactory, "maxSearchResults", MAX_SEARCH_RESULTS);
        ReflectionTestUtils.setField(searchOptionsFactory, "minSimilarityScore", MIN_SIMILARITY_SCORE);
        ReflectionTestUtils.setField(searchOptionsFactory, "recencyMonths", RECENCY_MONTHS);
    }

    @Nested
    @DisplayName("create - Score Fusion")
    class ScoreFusion {

        @Test
        @DisplayName("최신성 감지 여부와 무관하게 Score Fusion을 항상 켠다")
        void alwaysEnablesScoreFusion() {
            // Given
            SearchQuery withoutRecency = searchQuery(false);
            SearchQuery withRecency = searchQuery(true);

            // When
            SearchOptions optionsWithoutRecency = searchOptionsFactory.create(withoutRecency);
            SearchOptions optionsWithRecency = searchOptionsFactory.create(withRecency);

            // Then
            assertThat(optionsWithoutRecency.enableScoreFusion()).isTrue();
            assertThat(optionsWithRecency.enableScoreFusion()).isTrue();
        }
    }

    @Nested
    @DisplayName("create - 최신성 기간")
    class Recency {

        @Test
        @DisplayName("최신성 감지 시 dateFrom이 recencyMonths 이전 시각")
        void recencyDetected_dateFromIsMonthsAgo() {
            // Given
            LocalDateTime before = LocalDateTime.now().minusMonths(RECENCY_MONTHS);
            SearchQuery searchQuery = searchQuery(true);

            // When
            SearchOptions options = searchOptionsFactory.create(searchQuery);

            // Then: LocalDateTime.now()를 감싸지 않으므로 호출 전후 범위로 단언한다
            LocalDateTime after = LocalDateTime.now().minusMonths(RECENCY_MONTHS);
            assertThat(options.recencyDetected()).isTrue();
            assertThat(options.dateFrom()).isBetween(before, after);
        }

        @Test
        @DisplayName("최신성 미감지 시 dateFrom이 null")
        void recencyNotDetected_dateFromIsNull() {
            // Given
            SearchQuery searchQuery = searchQuery(false);

            // When
            SearchOptions options = searchOptionsFactory.create(searchQuery);

            // Then
            assertThat(options.recencyDetected()).isFalse();
            assertThat(options.dateFrom()).isNull();
        }
    }

    @Nested
    @DisplayName("create - 필터 이전")
    class Filters {

        @Test
        @DisplayName("감지한 provider와 updateType을 그대로 옮긴다")
        void copiesDetectedFilters() {
            // Given
            SearchContext context = new SearchContext();
            context.addCollection("emerging_techs");
            context.addDetectedProvider("OPENAI");
            context.addDetectedProvider("ANTHROPIC");
            context.addDetectedUpdateType("SDK_RELEASE");
            SearchQuery searchQuery = SearchQuery.builder()
                .query("OpenAI Anthropic SDK")
                .context(context)
                .build();

            // When
            SearchOptions options = searchOptionsFactory.create(searchQuery);

            // Then
            assertThat(options.providerFilters()).containsExactly("OPENAI", "ANTHROPIC");
            assertThat(options.updateTypeFilters()).containsExactly("SDK_RELEASE");
            assertThat(options.includeEmergingTechs()).isTrue();
            assertThat(options.maxResults()).isEqualTo(MAX_SEARCH_RESULTS);
            assertThat(options.minSimilarityScore()).isEqualTo(MIN_SIMILARITY_SCORE);
        }

        @Test
        @DisplayName("감지된 필터가 없으면 빈 리스트를 옮긴다")
        void copiesEmptyFilters() {
            // Given
            SearchQuery searchQuery = searchQuery(false);

            // When
            SearchOptions options = searchOptionsFactory.create(searchQuery);

            // Then
            assertThat(options.providerFilters()).isEmpty();
            assertThat(options.updateTypeFilters()).isEmpty();
        }
    }

    private SearchQuery searchQuery(boolean recencyDetected) {
        SearchContext context = new SearchContext();
        context.addCollection("emerging_techs");
        context.setRecencyDetected(recencyDetected);
        return SearchQuery.builder()
            .query("AI 업데이트")
            .context(context)
            .build();
    }
}
