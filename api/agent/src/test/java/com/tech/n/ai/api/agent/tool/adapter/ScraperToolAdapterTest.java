package com.tech.n.ai.api.agent.tool.adapter;

import com.tech.n.ai.api.agent.tool.dto.ScrapedContentDto;
import com.tech.n.ai.client.scraper.util.RobotsTxtChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ScraperToolAdapter 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScraperToolAdapter 단위 테스트")
class ScraperToolAdapterTest {

    @Mock
    private RobotsTxtChecker robotsTxtChecker;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ScraperToolAdapter adapter;

    @BeforeEach
    void setUp() {
        WebClient.Builder builder = WebClient.builder();

        // WebClient.Builder mock이 아닌 실제 빌더를 사용하면서 WebClient를 mock으로 대체
        // ScraperToolAdapter 생성자가 Builder.build()를 호출하므로 직접 주입
        adapter = new ScraperToolAdapter(robotsTxtChecker, WebClient.builder());
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "webClient", webClient);
    }

    // ========== scrape 테스트 ==========

    @Nested
    @DisplayName("scrape")
    class Scrape {

        @Test
        @DisplayName("robots.txt 차단 시 차단 메시지 반환")
        void scrape_robotsTxt차단() {
            // Given
            String url = "https://example.com/page";
            when(robotsTxtChecker.check("https://example.com", "/page"))
                .thenReturn(RobotsTxtChecker.CheckResult.BLOCKED_BY_ROBOTS);

            // When
            ScrapedContentDto result = adapter.scrape(url);

            // Then
            assertThat(result.title()).isNull();
            assertThat(result.content()).contains(RobotsTxtChecker.CheckResult.BLOCKED_BY_ROBOTS.getMessage());
            assertThat(result.url()).isEqualTo(url);
        }

        @Test
        @DisplayName("정상 스크래핑 - article 태그 콘텐츠 추출")
        @SuppressWarnings("unchecked")
        void scrape_정상_article태그() {
            // Given
            String url = "https://example.com/blog";
            String html = "<html><head><title>테스트 제목</title></head><body>"
                + "<article>기사 내용입니다.</article></body></html>";

            when(robotsTxtChecker.check(anyString(), anyString()))
                .thenReturn(RobotsTxtChecker.CheckResult.ALLOWED);
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(html));

            // When
            ScrapedContentDto result = adapter.scrape(url);

            // Then
            assertThat(result.title()).isEqualTo("테스트 제목");
            assertThat(result.content()).contains("기사 내용입니다.");
            assertThat(result.url()).isEqualTo(url);
        }

        @Test
        @DisplayName("article 없으면 main 태그 폴백")
        @SuppressWarnings("unchecked")
        void scrape_main태그폴백() {
            // Given
            String url = "https://example.com/page";
            String html = "<html><head><title>제목</title></head><body>"
                + "<main>메인 콘텐츠입니다.</main></body></html>";

            when(robotsTxtChecker.check(anyString(), anyString()))
                .thenReturn(RobotsTxtChecker.CheckResult.ALLOWED);
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(html));

            // When
            ScrapedContentDto result = adapter.scrape(url);

            // Then
            assertThat(result.content()).contains("메인 콘텐츠입니다.");
        }

        @Test
        @DisplayName("셀렉터 모두 실패 시 body 전체 텍스트")
        @SuppressWarnings("unchecked")
        void scrape_body폴백() {
            // Given
            String url = "https://example.com/page";
            String html = "<html><head><title>제목</title></head><body>"
                + "<div>일반 내용입니다.</div></body></html>";

            when(robotsTxtChecker.check(anyString(), anyString()))
                .thenReturn(RobotsTxtChecker.CheckResult.ALLOWED);
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(html));

            // When
            ScrapedContentDto result = adapter.scrape(url);

            // Then
            assertThat(result.content()).contains("일반 내용입니다.");
        }

        @Test
        @DisplayName("빈 HTML 응답")
        @SuppressWarnings("unchecked")
        void scrape_빈HTML() {
            // Given
            String url = "https://example.com/empty";
            when(robotsTxtChecker.check(anyString(), anyString()))
                .thenReturn(RobotsTxtChecker.CheckResult.ALLOWED);
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(""));

            // When
            ScrapedContentDto result = adapter.scrape(url);

            // Then
            assertThat(result.title()).isNull();
            assertThat(result.content()).contains("페이지 내용을 가져올 수 없습니다.");
        }

        @Test
        @DisplayName("WebClient 예외 발생 시 에러 메시지 반환")
        @SuppressWarnings("unchecked")
        void scrape_예외() {
            // Given
            String url = "https://example.com/error";
            when(robotsTxtChecker.check(anyString(), anyString()))
                .thenReturn(RobotsTxtChecker.CheckResult.ALLOWED);
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("Connection refused"));

            // When
            ScrapedContentDto result = adapter.scrape(url);

            // Then
            assertThat(result.title()).isNull();
            assertThat(result.content()).contains("크롤링 실패");
            assertThat(result.url()).isEqualTo(url);
        }

        @Test
        @DisplayName("콘텐츠 2000자 초과 시 절단")
        @SuppressWarnings("unchecked")
        void scrape_긴콘텐츠절단() {
            // Given
            String url = "https://example.com/long";
            String longContent = "A".repeat(3000);
            String html = "<html><head><title>제목</title></head><body>"
                + "<article>" + longContent + "</article></body></html>";

            when(robotsTxtChecker.check(anyString(), anyString()))
                .thenReturn(RobotsTxtChecker.CheckResult.ALLOWED);
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(html));

            // When
            ScrapedContentDto result = adapter.scrape(url);

            // Then
            assertThat(result.content()).hasSize(2003); // 2000 + "..."
            assertThat(result.content()).endsWith("...");
        }
    }
}
