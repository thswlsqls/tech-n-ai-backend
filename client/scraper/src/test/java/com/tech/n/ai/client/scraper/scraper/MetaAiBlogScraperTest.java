package com.tech.n.ai.client.scraper.scraper;

import com.tech.n.ai.client.scraper.config.ScraperProperties;
import com.tech.n.ai.client.scraper.dto.ScrapedTechArticle;
import com.tech.n.ai.client.scraper.exception.ScrapingException;
import com.tech.n.ai.client.scraper.util.RobotsTxtChecker;
import com.tech.n.ai.client.scraper.util.StructuredDataDateExtractor;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.jsoup.HttpStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MetaAiBlogScraper 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetaAiBlogScraper 단위 테스트")
class MetaAiBlogScraperTest {

    private static final String BASE_URL = "https://ai.meta.com";
    private static final String BLOG_HTML =
        "<html><body><a href=\"/blog/llama-update\"><h3>Llama Update</h3></a></body></html>";

    @Mock
    private RobotsTxtChecker robotsTxtChecker;

    @Mock
    private StructuredDataDateExtractor dateExtractor;

    private ScraperProperties properties;
    private RetryRegistry retryRegistry;
    private AtomicInteger requestCount;

    @BeforeEach
    void setUp() {
        properties = new ScraperProperties();
        ScraperProperties.ScraperSourceConfig config = new ScraperProperties.ScraperSourceConfig();
        config.setBaseUrl(BASE_URL);
        properties.getSources().put("meta-ai-blog", config);

        // application-scraper.yml의 scraperRetry와 같은 조건(대기 시간만 짧게)
        retryRegistry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(WebClientException.class, IOException.class, HttpStatusException.class)
            .build());
        requestCount = new AtomicInteger();
    }

    private MetaAiBlogScraper scraperWith(ExchangeFunction exchangeFunction) {
        return scraperWith(exchangeFunction, false, Optional.empty());
    }

    private MetaAiBlogScraper scraperWith(ExchangeFunction exchangeFunction, boolean requiresSelenium,
                                          Optional<WebDriver> webDriver) {
        properties.getSources().values().forEach(config -> config.setRequiresSelenium(requiresSelenium));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requestCount.incrementAndGet();
            return exchangeFunction.exchange(request);
        });
        return new MetaAiBlogScraper(builder, robotsTxtChecker, properties, retryRegistry, webDriver, dateExtractor);
    }

    private static Mono<ClientResponse> htmlResponse(String html) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
            .header("Content-Type", "text/html")
            .body(html)
            .build());
    }

    private static WebClientRequestException connectionFailure(ClientRequest request) {
        return new WebClientRequestException(
            new ConnectException("Connection refused"), request.method(), request.url(), request.headers());
    }

    @Nested
    @DisplayName("scrapeArticles")
    class ScrapeArticles {

        @Test
        @DisplayName("WebClient 경로에서 첫 요청이 WebClientRequestException으로 실패하면 다시 요청해 기사를 돌려준다")
        void retriesAfterWebClientException() {
            // Given: 첫 요청은 연결 실패, 두 번째 요청은 /blog/<slug> 링크가 든 HTML
            given(robotsTxtChecker.isAllowed(BASE_URL, "/blog/")).willReturn(true);
            MetaAiBlogScraper scraper = scraperWith(request -> requestCount.get() == 1
                ? Mono.error(connectionFailure(request))
                : htmlResponse(BLOG_HTML));

            // When
            List<ScrapedTechArticle> articles = scraper.scrapeArticles();

            // Then: 예외 없이 기사가 나오고 요청은 정확히 2번
            assertThat(articles).isNotEmpty();
            assertThat(articles.get(0).url()).isEqualTo(BASE_URL + "/blog/llama-update");
            assertThat(requestCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("모든 시도가 WebClientException이면 3번 요청한 뒤 마지막 예외를 cause로 담은 ScrapingException을 던진다")
        void wrapsLastWebClientExceptionAfterAllAttempts() {
            // Given: 매번 연결 실패
            given(robotsTxtChecker.isAllowed(BASE_URL, "/blog/")).willReturn(true);
            AtomicReference<WebClientRequestException> lastError = new AtomicReference<>();
            MetaAiBlogScraper scraper = scraperWith(request -> {
                WebClientRequestException error = connectionFailure(request);
                lastError.set(error);
                return Mono.error(error);
            });

            // When & Then: WebClientException이 그대로 새지 않고 ScrapingException으로 감싸진다
            assertThatThrownBy(scraper::scrapeArticles)
                .isInstanceOf(ScrapingException.class)
                .satisfies(e -> assertThat(e.getCause()).isSameAs(lastError.get()));
            assertThat(requestCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("빈 HTML은 네트워크 오류가 아니므로 다시 요청하지 않고 ScrapingException을 던진다")
        void doesNotRetryOnEmptyHtml() {
            // Given: 빈 본문
            given(robotsTxtChecker.isAllowed(BASE_URL, "/blog/")).willReturn(true);
            MetaAiBlogScraper scraper = scraperWith(request -> htmlResponse(""));

            // When & Then
            assertThatThrownBy(scraper::scrapeArticles).isInstanceOf(ScrapingException.class);
            assertThat(requestCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("소스 설정이 없으면 HTTP 요청 없이 ScrapingException을 던진다")
        void throwsWithoutRequestWhenConfigMissing() {
            // Given: meta-ai-blog 설정 없음
            properties.getSources().clear();
            MetaAiBlogScraper scraper = scraperWith(request -> htmlResponse(BLOG_HTML));

            // When & Then
            assertThatThrownBy(scraper::scrapeArticles)
                .isInstanceOf(ScrapingException.class)
                .hasMessage("Meta AI Blog scraper source configuration not found");
            assertThat(requestCount.get()).isZero();
        }

        @Test
        @DisplayName("robots.txt가 막으면 HTTP 요청 없이 ScrapingException을 던진다")
        void throwsWithoutRequestWhenRobotsDisallow() {
            // Given: robots.txt 차단
            given(robotsTxtChecker.isAllowed(anyString(), anyString())).willReturn(false);
            MetaAiBlogScraper scraper = scraperWith(request -> htmlResponse(BLOG_HTML));

            // When & Then
            assertThatThrownBy(scraper::scrapeArticles)
                .isInstanceOf(ScrapingException.class)
                .hasMessage("Scraping not allowed by robots.txt for Meta AI Blog");
            assertThat(requestCount.get()).isZero();
        }

        @Test
        @DisplayName("Selenium 경로 실패는 지금처럼 다시 시도하지 않고 ScrapingException으로 감싸 던진다")
        void doesNotRetrySeleniumFailure() {
            // Given: requiresSelenium=true이고 WebDriver 페이지 로드가 실패
            given(robotsTxtChecker.isAllowed(BASE_URL, "/blog/")).willReturn(true);
            WebDriver driver = mock(WebDriver.class);
            willThrow(new WebDriverException("page load failed")).given(driver).get(anyString());
            MetaAiBlogScraper scraper = scraperWith(request -> htmlResponse(BLOG_HTML), true, Optional.of(driver));

            // When & Then: WebDriver는 한 번만 호출되고 WebClient 요청은 없다
            assertThatThrownBy(scraper::scrapeArticles)
                .isInstanceOf(ScrapingException.class)
                .hasMessage("Meta AI Blog scraping failed");
            verify(driver, times(1)).get(BASE_URL + "/blog/");
            assertThat(requestCount.get()).isZero();
        }
    }
}
