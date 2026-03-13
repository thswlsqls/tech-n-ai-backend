package com.tech.n.ai.api.agent.tool;

import com.tech.n.ai.api.agent.metrics.ToolExecutionMetrics;
import com.tech.n.ai.api.agent.tool.adapter.*;
import com.tech.n.ai.api.agent.tool.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EmergingTechAgentTools 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmergingTechAgentTools 단위 테스트")
class EmergingTechAgentToolsTest {

    @Mock
    private GitHubToolAdapter githubAdapter;

    @Mock
    private ScraperToolAdapter scraperAdapter;

    @Mock
    private SlackToolAdapter slackAdapter;

    @Mock
    private EmergingTechToolAdapter emergingTechAdapter;

    @Mock
    private AnalyticsToolAdapter analyticsAdapter;

    @Mock
    private DataCollectionToolAdapter dataCollectionAdapter;

    @InjectMocks
    private EmergingTechAgentTools tools;

    private ToolExecutionMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new ToolExecutionMetrics();
        tools.bindMetrics(metrics);
    }

    @AfterEach
    void tearDown() {
        tools.unbindMetrics();
    }

    // ========== fetchGitHubReleases 테스트 ==========

    @Nested
    @DisplayName("fetchGitHubReleases")
    class FetchGitHubReleases {

        @Test
        @DisplayName("정상 호출 - 릴리즈 목록 반환")
        void fetchGitHubReleases_정상호출() {
            // Given
            List<GitHubReleaseDto> releases = List.of(
                    new GitHubReleaseDto("v1.0.0", "Release", "notes", "url", "2024-01-15")
            );
            when(githubAdapter.getReleases("openai", "openai-python")).thenReturn(releases);

            // When
            List<GitHubReleaseDto> result = tools.fetchGitHubReleases("openai", "openai-python");

            // Then
            assertThat(result).hasSize(1);
            verify(githubAdapter).getReleases("openai", "openai-python");
        }

        @Test
        @DisplayName("owner 교정 (anthropic → anthropics)")
        void fetchGitHubReleases_owner교정() {
            // Given
            when(githubAdapter.getReleases("anthropics", "sdk")).thenReturn(List.of());

            // When
            tools.fetchGitHubReleases("anthropic", "sdk");

            // Then
            verify(githubAdapter).getReleases("anthropics", "sdk");
        }

        @Test
        @DisplayName("검증 실패 시 빈 리스트 반환")
        void fetchGitHubReleases_검증실패() {
            // When - owner가 빈 문자열인 경우 (null은 NullPointerException 발생)
            List<GitHubReleaseDto> result = tools.fetchGitHubReleases("", "repo");

            // Then
            assertThat(result).isEmpty();
            verify(githubAdapter, never()).getReleases(any(), any());
        }

        @Test
        @DisplayName("메트릭 증가 확인 (toolCallCount)")
        void fetchGitHubReleases_메트릭증가() {
            // Given
            when(githubAdapter.getReleases(any(), any())).thenReturn(List.of());

            // When
            tools.fetchGitHubReleases("owner", "repo");

            // Then
            assertThat(metrics.getToolCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("검증 실패 시 validationErrorCount 증가")
        void fetchGitHubReleases_검증실패_메트릭() {
            // When
            tools.fetchGitHubReleases("", "");

            // Then
            assertThat(metrics.getValidationErrorCount()).isEqualTo(1);
        }
    }

    // ========== scrapeWebPage 테스트 ==========

    @Nested
    @DisplayName("scrapeWebPage")
    class ScrapeWebPage {

        @Test
        @DisplayName("정상 크롤링")
        void scrapeWebPage_정상크롤링() {
            // Given
            ScrapedContentDto content = new ScrapedContentDto("Title", "Content", "https://example.com");
            when(scraperAdapter.scrape("https://example.com")).thenReturn(content);

            // When
            ScrapedContentDto result = tools.scrapeWebPage("https://example.com");

            // Then
            assertThat(result.title()).isEqualTo("Title");
            verify(scraperAdapter).scrape("https://example.com");
        }

        @Test
        @DisplayName("URL 검증 실패 시 에러 DTO 반환")
        void scrapeWebPage_URL검증실패() {
            // When
            ScrapedContentDto result = tools.scrapeWebPage("invalid-url");

            // Then
            assertThat(result.title()).isNull();
            assertThat(result.content()).contains("Error");
            verify(scraperAdapter, never()).scrape(any());
        }

        @Test
        @DisplayName("메트릭 증가 확인")
        void scrapeWebPage_메트릭증가() {
            // Given
            when(scraperAdapter.scrape(any())).thenReturn(new ScrapedContentDto(null, null, null));

            // When
            tools.scrapeWebPage("https://example.com");

            // Then
            assertThat(metrics.getToolCallCount()).isEqualTo(1);
        }
    }

    // ========== searchEmergingTechs 테스트 ==========

    @Nested
    @DisplayName("searchEmergingTechs")
    class SearchEmergingTechs {

        @Test
        @DisplayName("정상 검색")
        void searchEmergingTechs_정상검색() {
            // Given
            List<EmergingTechDto> techs = List.of(
                    new EmergingTechDto("id1", "OPENAI", "MODEL_RELEASE", "GPT-5", "url", "PUBLISHED")
            );
            when(emergingTechAdapter.search("GPT", "OPENAI")).thenReturn(techs);

            // When
            List<EmergingTechDto> result = tools.searchEmergingTechs("GPT", "OPENAI");

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("query 검증 실패 시 빈 리스트")
        void searchEmergingTechs_query검증실패() {
            // When
            List<EmergingTechDto> result = tools.searchEmergingTechs("", "OPENAI");

            // Then
            assertThat(result).isEmpty();
            assertThat(metrics.getValidationErrorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("provider 검증 실패 시 빈 리스트")
        void searchEmergingTechs_provider검증실패() {
            // When
            List<EmergingTechDto> result = tools.searchEmergingTechs("query", "INVALID");

            // Then
            assertThat(result).isEmpty();
            assertThat(metrics.getValidationErrorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 provider (전체 검색)")
        void searchEmergingTechs_빈provider() {
            // Given
            when(emergingTechAdapter.search("query", "")).thenReturn(List.of());

            // When
            List<EmergingTechDto> result = tools.searchEmergingTechs("query", "");

            // Then
            assertThat(result).isEmpty();
            verify(emergingTechAdapter).search("query", "");
        }
    }

    // ========== listEmergingTechs 테스트 ==========

    @Nested
    @DisplayName("listEmergingTechs")
    class ListEmergingTechs {

        @Test
        @DisplayName("정상 목록 조회")
        void listEmergingTechs_정상조회() {
            // Given
            EmergingTechListDto listDto = new EmergingTechListDto(100, 1, 20, 5, "전체", List.of());
            when(emergingTechAdapter.list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(listDto);

            // When
            EmergingTechListDto result = tools.listEmergingTechs("", "", "", "", "", "", 1, 20);

            // Then
            assertThat(result.totalCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("날짜 검증 실패 시 empty 반환")
        void listEmergingTechs_날짜검증실패() {
            // When
            EmergingTechListDto result = tools.listEmergingTechs("invalid", "", "", "", "", "", 1, 20);

            // Then
            assertThat(result.items()).isEmpty();
            assertThat(metrics.getValidationErrorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("페이지 정규화 적용")
        void listEmergingTechs_페이지정규화() {
            // Given
            when(emergingTechAdapter.list(any(), any(), any(), any(), any(), any(), eq(1), eq(20)))
                    .thenReturn(EmergingTechListDto.empty(1, 20, "전체"));

            // When - 음수 페이지, 0 사이즈 입력
            tools.listEmergingTechs("", "", "", "", "", "", -5, 0);

            // Then - 정규화된 값 (1, 20)으로 호출됨
            verify(emergingTechAdapter).list(any(), any(), any(), any(), any(), any(), eq(1), eq(20));
        }
    }

    // ========== getEmergingTechDetail 테스트 ==========

    @Nested
    @DisplayName("getEmergingTechDetail")
    class GetEmergingTechDetail {

        @Test
        @DisplayName("정상 상세 조회")
        void getEmergingTechDetail_정상조회() {
            // Given
            EmergingTechDetailDto detail = new EmergingTechDetailDto(
                    "507f1f77bcf86cd799439011", "OPENAI", "MODEL_RELEASE",
                    "Title", "Summary", "url", "2024-01-15",
                    "RSS", "PUBLISHED", "ext-id", null, null, null
            );
            when(emergingTechAdapter.getDetail("507f1f77bcf86cd799439011")).thenReturn(detail);

            // When
            EmergingTechDetailDto result = tools.getEmergingTechDetail("507f1f77bcf86cd799439011");

            // Then
            assertThat(result.id()).isEqualTo("507f1f77bcf86cd799439011");
        }

        @Test
        @DisplayName("ID 검증 실패 시 notFound 반환")
        void getEmergingTechDetail_ID검증실패() {
            // When
            EmergingTechDetailDto result = tools.getEmergingTechDetail("invalid-id");

            // Then
            assertThat(result.provider()).isNull();
            assertThat(result.summary()).contains("찾을 수 없습니다");
            verify(emergingTechAdapter, never()).getDetail(any());
        }
    }

    // ========== getStatistics 테스트 ==========

    @Nested
    @DisplayName("getStatistics")
    class GetStatistics {

        @Test
        @DisplayName("정상 통계 조회")
        void getStatistics_정상조회() {
            // Given
            StatisticsDto stats = new StatisticsDto("provider", "", "", 100,
                    List.of(new StatisticsDto.GroupCount("OPENAI", 50)));
            when(analyticsAdapter.getStatistics("provider", "", "")).thenReturn(stats);

            // When
            StatisticsDto result = tools.getStatistics("provider", "", "");

            // Then
            assertThat(result.totalCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("groupBy 검증 실패 시 빈 통계")
        void getStatistics_groupBy검증실패() {
            // When
            StatisticsDto result = tools.getStatistics("invalid", "", "");

            // Then
            assertThat(result.groups()).isEmpty();
            assertThat(metrics.getValidationErrorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("analyticsCallCount 증가 확인")
        void getStatistics_analyticsCallCount증가() {
            // Given
            when(analyticsAdapter.getStatistics(any(), any(), any()))
                    .thenReturn(new StatisticsDto("provider", "", "", 0, List.of()));

            // When
            tools.getStatistics("provider", "", "");

            // Then
            assertThat(metrics.getAnalyticsCallCount()).isEqualTo(1);
            assertThat(metrics.getToolCallCount()).isEqualTo(1);
        }
    }

    // ========== analyzeTextFrequency 테스트 ==========

    @Nested
    @DisplayName("analyzeTextFrequency")
    class AnalyzeTextFrequency {

        @Test
        @DisplayName("정상 분석")
        void analyzeTextFrequency_정상분석() {
            // Given
            WordFrequencyDto wordFreq = new WordFrequencyDto(50, "전체", List.of(), List.of());
            when(analyticsAdapter.analyzeTextFrequency(any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(wordFreq);

            // When
            WordFrequencyDto result = tools.analyzeTextFrequency("OPENAI", "", "", "", "", 20);

            // Then
            assertThat(result.totalDocuments()).isEqualTo(50);
            assertThat(metrics.getAnalyticsCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("provider 검증 실패 시 빈 결과")
        void analyzeTextFrequency_provider검증실패() {
            // When
            WordFrequencyDto result = tools.analyzeTextFrequency("INVALID", "", "", "", "", 20);

            // Then
            assertThat(result.topWords()).isEmpty();
            assertThat(metrics.getValidationErrorCount()).isEqualTo(1);
        }
    }

    // ========== sendSlackNotification 테스트 ==========

    @Nested
    @DisplayName("sendSlackNotification")
    class SendSlackNotification {

        @Test
        @DisplayName("정상 전송")
        void sendSlackNotification_정상전송() {
            // Given
            when(slackAdapter.sendNotification("message")).thenReturn(ToolResult.success("전송 완료"));

            // When
            ToolResult result = tools.sendSlackNotification("message");

            // Then
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("메시지 검증 실패")
        void sendSlackNotification_메시지검증실패() {
            // When
            ToolResult result = tools.sendSlackNotification("");

            // Then
            assertThat(result.success()).isFalse();
            verify(slackAdapter, never()).sendNotification(any());
        }
    }

    // ========== collectGitHubReleases 테스트 ==========

    @Nested
    @DisplayName("collectGitHubReleases")
    class CollectGitHubReleases {

        @Test
        @DisplayName("정상 수집")
        void collectGitHubReleases_정상수집() {
            // Given
            DataCollectionResultDto collectResult = DataCollectionResultDto.success(
                    "GITHUB_RELEASES", "OPENAI", 5, 5, 3, 2, 0, List.of());
            when(dataCollectionAdapter.collectGitHubReleases("openai", "openai-python"))
                    .thenReturn(collectResult);

            // When
            DataCollectionResultDto result = tools.collectGitHubReleases("openai", "openai-python");

            // Then
            assertThat(result.newCount()).isEqualTo(3);
            assertThat(result.failureCount()).isZero();
        }

        @Test
        @DisplayName("owner 교정 후 수집")
        void collectGitHubReleases_owner교정() {
            // Given
            DataCollectionResultDto collectResult = DataCollectionResultDto.success(
                    "GITHUB_RELEASES", "ANTHROPIC", 1, 1, 1, 0, 0, List.of());
            when(dataCollectionAdapter.collectGitHubReleases("anthropics", "sdk"))
                    .thenReturn(collectResult);

            // When
            tools.collectGitHubReleases("anthropic", "sdk");

            // Then
            verify(dataCollectionAdapter).collectGitHubReleases("anthropics", "sdk");
        }

        @Test
        @DisplayName("검증 실패 시 failure 반환")
        void collectGitHubReleases_검증실패() {
            // When
            DataCollectionResultDto result = tools.collectGitHubReleases("", "repo");

            // Then
            assertThat(result.failureMessages()).isNotEmpty();
            verify(dataCollectionAdapter, never()).collectGitHubReleases(any(), any());
        }
    }

    // ========== collectRssFeeds 테스트 ==========

    @Nested
    @DisplayName("collectRssFeeds")
    class CollectRssFeeds {

        @Test
        @DisplayName("정상 수집")
        void collectRssFeeds_정상수집() {
            // Given
            DataCollectionResultDto collectResult = DataCollectionResultDto.success(
                    "RSS_FEEDS", "OPENAI", 10, 10, 8, 2, 0, List.of());
            when(dataCollectionAdapter.collectRssFeeds("OPENAI")).thenReturn(collectResult);

            // When
            DataCollectionResultDto result = tools.collectRssFeeds("OPENAI");

            // Then
            assertThat(result.newCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("잘못된 provider 검증 실패")
        void collectRssFeeds_잘못된provider() {
            // When
            DataCollectionResultDto result = tools.collectRssFeeds("INVALID");

            // Then
            assertThat(result.failureMessages()).isNotEmpty();
            assertThat(result.summary()).contains("실패");
        }
    }

    // ========== collectScrapedArticles 테스트 ==========

    @Nested
    @DisplayName("collectScrapedArticles")
    class CollectScrapedArticles {

        @Test
        @DisplayName("정상 수집")
        void collectScrapedArticles_정상수집() {
            // Given
            DataCollectionResultDto collectResult = DataCollectionResultDto.success(
                    "WEB_SCRAPING", "ANTHROPIC", 5, 5, 5, 0, 0, List.of());
            when(dataCollectionAdapter.collectScrapedArticles("ANTHROPIC")).thenReturn(collectResult);

            // When
            DataCollectionResultDto result = tools.collectScrapedArticles("ANTHROPIC");

            // Then
            assertThat(result.newCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("잘못된 provider 검증 실패")
        void collectScrapedArticles_잘못된provider() {
            // When
            DataCollectionResultDto result = tools.collectScrapedArticles("INVALID_PROVIDER");

            // Then
            assertThat(result.failureMessages()).isNotEmpty();
            assertThat(result.summary()).contains("실패");
        }
    }

    // ========== 메트릭 바인딩 테스트 ==========

    @Nested
    @DisplayName("메트릭 바인딩")
    class MetricsBinding {

        @Test
        @DisplayName("bindMetrics 후 정상 동작")
        void bindMetrics_정상동작() {
            // Given - setUp에서 이미 바인딩됨
            when(githubAdapter.getReleases(any(), any())).thenReturn(List.of());

            // When
            tools.fetchGitHubReleases("owner", "repo");

            // Then
            assertThat(metrics.getToolCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("unbindMetrics 후 예외 발생")
        void unbindMetrics_예외발생() {
            // Given
            tools.unbindMetrics();

            // When & Then
            assertThatThrownBy(() -> tools.fetchGitHubReleases("owner", "repo"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("바인딩되지 않았습니다");
        }

        @Test
        @DisplayName("메트릭 미바인딩 시 IllegalStateException")
        void metricsNotBound_예외() {
            // Given
            EmergingTechAgentTools newTools = new EmergingTechAgentTools(
                    githubAdapter, scraperAdapter, slackAdapter,
                    emergingTechAdapter, analyticsAdapter, dataCollectionAdapter);
            // 메트릭 바인딩 없이 사용

            // When & Then
            assertThatThrownBy(() -> newTools.fetchGitHubReleases("owner", "repo"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
