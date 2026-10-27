package com.tech.n.ai.client.scraper.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ScraperConfig 단위 테스트 (Spring 컨텍스트 없이 JDK HttpServer로 확인)
 */
@DisplayName("ScraperConfig 단위 테스트")
class ScraperConfigTest {

    private static final int SLOW_RESPONSE_DELAY_MILLIS = 3000;
    private static final int MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicReference<String> receivedUserAgent = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);

        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(SLOW_RESPONSE_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
            respond(exchange, "late".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/fast", exchange -> {
            receivedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, "ok".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/large", exchange -> respond(exchange, new byte[MAX_IN_MEMORY_SIZE + 1]));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private WebClient webClient(int timeoutSeconds, String userAgent) {
        ScraperProperties properties = new ScraperProperties();
        properties.setTimeoutSeconds(timeoutSeconds);
        properties.setUserAgent(userAgent);
        return new ScraperConfig().scraperWebClientBuilder(properties).build();
    }

    @Nested
    @DisplayName("scraperWebClientBuilder")
    class ScraperWebClientBuilder {

        @Test
        @DisplayName("응답이 timeoutSeconds보다 늦으면 3초를 기다리지 않고 WebClientRequestException으로 끝난다")
        void slowResponse_failsWithResponseTimeout() {
            // Given: timeoutSeconds=1, 서버는 3초 뒤에 응답
            WebClient client = webClient(1, "test-agent");

            // When
            long start = System.nanoTime();
            Throwable thrown = catchThrowable(() ->
                    client.get().uri(url("/slow")).retrieve().bodyToMono(String.class).block());
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            // Then: Reactor Netty 응답 타임아웃이 원인인 요청 예외로 끝나고, 서버 지연(3초)보다 먼저 끝난다
            assertThat(thrown)
                    .isInstanceOf(WebClientRequestException.class)
                    .hasRootCauseInstanceOf(ReadTimeoutException.class);
            assertThat(elapsedMillis).isLessThan(SLOW_RESPONSE_DELAY_MILLIS);
        }

        @Test
        @DisplayName("제때 응답하면 본문을 받고 User-Agent 헤더에 설정값이 실린다")
        void fastResponse_returnsBodyWithUserAgent() {
            // Given
            WebClient client = webClient(1, "test-agent/1.0");

            // When
            String body = client.get().uri(url("/fast")).retrieve().bodyToMono(String.class).block();

            // Then
            assertThat(body).isEqualTo("ok");
            assertThat(receivedUserAgent.get()).isEqualTo("test-agent/1.0");
        }

        @Test
        @DisplayName("10MB를 넘는 본문은 메모리 버퍼 제한으로 거부된다")
        void largeResponse_exceedsMaxInMemorySize() {
            // Given
            WebClient client = webClient(5, "test-agent");

            // When & Then
            assertThatThrownBy(() ->
                    client.get().uri(url("/large")).retrieve().bodyToMono(String.class).block())
                    .isInstanceOf(WebClientResponseException.class)
                    .hasRootCauseInstanceOf(DataBufferLimitException.class);
        }

        @Test
        @DisplayName("빈 이름은 scraperWebClientBuilder 그대로다")
        void beanName_isScraperWebClientBuilder() throws NoSuchMethodException {
            // Given & When
            Bean bean = ScraperConfig.class
                    .getMethod("scraperWebClientBuilder", ScraperProperties.class)
                    .getAnnotation(Bean.class);

            // Then
            assertThat(bean.value()).containsExactly("scraperWebClientBuilder");
        }
    }
}
