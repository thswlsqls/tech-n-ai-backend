package com.tech.n.ai.client.slack.domain.slack.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.tech.n.ai.client.slack.config.SlackProperties;
import com.tech.n.ai.client.slack.util.SlackRateLimiter;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SlackWebhookClient 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlackWebhookClient 단위 테스트")
class SlackWebhookClientTest {

    private static final String SECRET = "SECRETVALUE";
    private static final String WEBHOOK_PATH = "/services/T000/B000/" + SECRET;

    @Mock
    private SlackRateLimiter rateLimiter;

    private final Logger logger = (Logger) LoggerFactory.getLogger(SlackWebhookClient.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;
    private DisposableServer server;

    @BeforeEach
    void setUp() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        if (server != null) {
            server.disposeNow();
        }
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("Slack이 404 no_service를 돌려주면 로그에 webhook 비밀 경로 없이 상태 코드와 응답 본문만 남긴다")
        void notFound_logsStatusAndBodyWithoutSecret() {
            // Given: 404와 본문 no_service를 돌려주는 로컬 서버
            server = startServer(HttpResponseStatus.NOT_FOUND, "no_service");
            SlackWebhookClient client = createClient("http://localhost:" + server.port() + WEBHOOK_PATH);

            // When: 메시지를 보낸다
            // Then: 예외를 던지지 않는다
            assertThatCode(() -> client.sendMessage("hello")).doesNotThrowAnyException();

            // Then: 어떤 로그에도 비밀값이 없고, ERROR 1건에 상태 코드와 본문이 들어 있다
            assertThat(allLogText()).doesNotContain(SECRET);
            List<ILoggingEvent> errors = errorEvents();
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getFormattedMessage()).contains("404").contains("no_service");
            verifyNoInteractions(rateLimiter);
        }

        @Test
        @DisplayName("연결이 실패하면 로그에 webhook 비밀 경로 없이 예외 클래스 이름을 남긴다")
        void connectionFailure_logsExceptionNameWithoutSecret() {
            // Given: 서버를 띄웠다가 바로 닫아 아무도 받지 않는 포트
            DisposableServer closed = startServer(HttpResponseStatus.OK, "ok");
            int closedPort = closed.port();
            closed.disposeNow();
            SlackWebhookClient client = createClient("http://localhost:" + closedPort + WEBHOOK_PATH);

            // When: 메시지를 보낸다
            // Then: 예외를 던지지 않는다
            assertThatCode(() -> client.sendMessage("hello")).doesNotThrowAnyException();

            // Then: 어떤 로그에도 비밀값이 없고, ERROR 1건에 예외 클래스 이름이 들어 있다
            assertThat(allLogText()).doesNotContain(SECRET);
            List<ILoggingEvent> errors = errorEvents();
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getFormattedMessage()).contains("WebClientRequestException");
        }

        @Test
        @DisplayName("Slack이 200을 돌려주면 ERROR 로그를 남기지 않는다")
        void ok_logsNoError() {
            // Given: 200 ok를 돌려주는 로컬 서버
            server = startServer(HttpResponseStatus.OK, "ok");
            SlackWebhookClient client = createClient("http://localhost:" + server.port() + WEBHOOK_PATH);

            // When: 메시지를 보낸다
            client.sendMessage("hello");

            // Then: ERROR 로그가 없다
            assertThat(errorEvents()).isEmpty();
        }
    }

    private DisposableServer startServer(HttpResponseStatus status, String body) {
        return HttpServer.create()
            .host("localhost")
            .port(0)
            .handle((request, response) -> response.status(status).sendString(Mono.just(body)))
            .bindNow();
    }

    private SlackWebhookClient createClient(String webhookUrl) {
        SlackProperties properties = new SlackProperties();
        properties.getWebhook().setUrl(webhookUrl);
        properties.getRateLimit().setEnabled(false);
        return new SlackWebhookClient(WebClient.create(), properties, rateLimiter);
    }

    private List<ILoggingEvent> errorEvents() {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
    }

    private String allLogText() {
        return appender.list.stream()
            .map(event -> event.getFormattedMessage()
                + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
            .collect(Collectors.joining("\n"));
    }
}
