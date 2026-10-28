package com.tech.n.ai.client.rss.parser;

import com.tech.n.ai.client.rss.config.RssProperties;
import com.tech.n.ai.client.rss.dto.RssFeedItem;
import com.tech.n.ai.client.rss.exception.RssParsingException;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * AbstractRssParser 단위 테스트
 */
@DisplayName("AbstractRssParser 단위 테스트")
class AbstractRssParserTest {

    private static final String FEED_URL = "https://example.com/feed";

    private static final String RSS_BODY = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>OpenAI Blog</title>
            <link>https://openai.com/blog</link>
            <description>OpenAI Blog</description>
            <item>
              <title>New model</title>
              <link>https://openai.com/blog/new-model</link>
              <description>desc</description>
              <pubDate>Mon, 26 Oct 2026 01:00:00 GMT</pubDate>
              <category>Research</category>
              <category>Product</category>
              <guid>https://openai.com/blog/new-model-guid</guid>
            </item>
          </channel>
        </rss>
        """;

    private final AtomicInteger requestCount = new AtomicInteger();
    private final Deque<Supplier<Mono<ClientResponse>>> responses = new ArrayDeque<>();
    private Supplier<Mono<ClientResponse>> lastResponse;

    private final ExchangeFunction exchangeFunction = request -> {
        requestCount.incrementAndGet();
        if (!responses.isEmpty()) {
            lastResponse = responses.poll();
        }
        return lastResponse.get();
    };

    private RssProperties properties;
    private RetryRegistry retryRegistry;

    @BeforeEach
    void setUp() {
        properties = new RssProperties();
        properties.getSources().put("openai-blog", sourceConfig());
        properties.getSources().put("techcrunch", sourceConfig());
        retryRegistry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(WebClientException.class)
            .build());
    }

    @Nested
    @DisplayName("parse - 요청 재시도")
    class Retry {

        @Test
        @DisplayName("요청이 매번 WebClientException으로 실패하면 3번 요청한 뒤 fetch 실패 RssParsingException을 던진다")
        void parse_requestAlwaysFails_retriesThenThrows() {
            // Given
            respondWith(AbstractRssParserTest::requestFailure);

            // When
            Throwable thrown = catchThrowable(() -> openAiParser().parse());

            // Then
            assertThat(requestCount).hasValue(3);
            assertThat(thrown)
                .isInstanceOf(RssParsingException.class)
                .hasMessageContaining("RSS feed fetch failed")
                .hasCauseInstanceOf(WebClientException.class);
        }

        @Test
        @DisplayName("첫 요청이 WebClientException으로 실패하고 두 번째 요청이 성공하면 항목 목록을 돌려준다")
        void parse_firstRequestFails_secondSucceeds() {
            // Given
            respondWith(AbstractRssParserTest::requestFailure, () -> ok(RSS_BODY));

            // When
            List<RssFeedItem> items = openAiParser().parse();

            // Then
            assertThat(requestCount).hasValue(2);
            assertThat(items).hasSize(1);
            assertThat(items.get(0).title()).isEqualTo("New model");
        }
    }

    @Nested
    @DisplayName("parse - 재시도하지 않는 실패")
    class NoRetry {

        @Test
        @DisplayName("응답 본문이 XML이 아니면 1번만 요청하고 파싱 실패 RssParsingException을 던진다")
        void parse_notXml_throwsWithoutRetry() {
            // Given
            respondWith(() -> ok("this is not xml"));

            // When
            Throwable thrown = catchThrowable(() -> openAiParser().parse());

            // Then
            assertThat(requestCount).hasValue(1);
            assertThat(thrown)
                .isInstanceOf(RssParsingException.class)
                .hasMessageContaining("RSS parsing failed");
        }

        @Test
        @DisplayName("응답 본문이 비어 있으면 1번만 요청하고 RssParsingException을 던진다")
        void parse_emptyBody_throwsWithoutRetry() {
            // Given
            respondWith(() -> Mono.just(ClientResponse.create(HttpStatus.OK).build()));

            // When
            Throwable thrown = catchThrowable(() -> openAiParser().parse());

            // Then
            assertThat(requestCount).hasValue(1);
            assertThat(thrown)
                .isInstanceOf(RssParsingException.class)
                .hasMessageContaining("Empty RSS feed content");
        }

        @Test
        @DisplayName("요청 단계에서 WebClientException이 아닌 런타임 예외가 나면 1번만 요청하고 RssParsingException으로 감싸 던진다")
        void parse_otherRuntimeExceptionOnFetch_wrapsInRssParsingException() {
            // Given
            IllegalStateException cause = new IllegalStateException("unexpected");
            respondWith(() -> Mono.error(cause));

            // When
            Throwable thrown = catchThrowable(() -> openAiParser().parse());

            // Then
            assertThat(requestCount).hasValue(1);
            assertThat(thrown)
                .isInstanceOf(RssParsingException.class)
                .hasCause(cause);
        }

        @Test
        @DisplayName("소스 설정이 없으면 요청 없이 RssParsingException을 던진다")
        void parse_noSourceConfig_throwsWithoutRequest() {
            // Given
            properties.getSources().clear();

            // When
            Throwable thrown = catchThrowable(() -> openAiParser().parse());

            // Then
            assertThat(requestCount).hasValue(0);
            assertThat(thrown)
                .isInstanceOf(RssParsingException.class)
                .hasMessageContaining("RSS source configuration not found");
        }
    }

    @Nested
    @DisplayName("parse - 정상 응답 변환")
    class Conversion {

        @Test
        @DisplayName("항목의 제목·링크·발행일·카테고리·guid를 변환하고 BOM과 앞뒤 공백을 지운 뒤 파싱한다")
        void parse_convertsItem() {
            // Given
            respondWith(() -> ok("\uFEFF  \n" + RSS_BODY));

            // When
            List<RssFeedItem> items = openAiParser().parse();

            // Then
            assertThat(items).hasSize(1);
            RssFeedItem item = items.get(0);
            assertThat(item.title()).isEqualTo("New model");
            assertThat(item.link()).isEqualTo("https://openai.com/blog/new-model");
            assertThat(item.publishedDate()).isEqualTo(
                LocalDateTime.ofInstant(Instant.parse("2026-10-26T01:00:00Z"), ZoneId.systemDefault()));
            assertThat(item.category()).isEqualTo("Research,Product");
            assertThat(item.guid()).isEqualTo("https://openai.com/blog/new-model-guid");
        }

        @Test
        @DisplayName("TechCrunch는 XML 선언 앞의 문자를 지우는 prepareContent 재정의를 거쳐 파싱한다")
        void parse_techCrunchPrepareContentApplied() {
            // Given
            respondWith(() -> ok("garbage" + RSS_BODY.strip()));

            // When
            List<RssFeedItem> items = new TechCrunchRssParser(webClientBuilder(), properties, retryRegistry).parse();

            // Then
            assertThat(requestCount).hasValue(1);
            assertThat(items).extracting(RssFeedItem::title).containsExactly("New model");
        }
    }

    private OpenAiBlogRssParser openAiParser() {
        return new OpenAiBlogRssParser(webClientBuilder(), properties, retryRegistry);
    }

    private WebClient.Builder webClientBuilder() {
        return WebClient.builder().exchangeFunction(exchangeFunction);
    }

    /**
     * 요청마다 차례로 응답을 돌려준다. 목록이 끝나면 마지막 응답을 반복한다.
     */
    @SafeVarargs
    private void respondWith(Supplier<Mono<ClientResponse>>... suppliers) {
        responses.addAll(List.of(suppliers));
    }

    private static RssProperties.RssSourceConfig sourceConfig() {
        RssProperties.RssSourceConfig config = new RssProperties.RssSourceConfig();
        config.setFeedUrl(FEED_URL);
        return config;
    }

    private static Mono<ClientResponse> requestFailure() {
        return Mono.error(new WebClientRequestException(
            new IOException("connection reset"), HttpMethod.GET, URI.create(FEED_URL), new HttpHeaders()));
    }

    private static Mono<ClientResponse> ok(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, "application/xml")
            .body(body)
            .build());
    }
}
