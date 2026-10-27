package com.tech.n.ai.client.scraper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RobotsTxtChecker 단위 테스트
 */
@DisplayName("RobotsTxtChecker 단위 테스트")
class RobotsTxtCheckerTest {

    private static final String BASE_URL = "https://www.anthropic.com";
    private static final String DISALLOW_NEWS = "User-agent: *\nDisallow: /news\n";

    private final List<String> requestedUrls = new ArrayList<>();

    /**
     * robots.txt 요청에 정해진 본문을 돌려주는 WebClient로 만든 checker
     */
    private RobotsTxtChecker checkerReturning(String robotsTxt) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requestedUrls.add(request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(robotsTxt)
                .build());
        });
        return new RobotsTxtChecker(builder);
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        @DisplayName("Disallow: /news이면 /news는 BLOCKED_BY_ROBOTS다")
        void check_disallowedPath_blocked() {
            // Given: /news만 막는 robots.txt
            RobotsTxtChecker checker = checkerReturning(DISALLOW_NEWS);

            // When
            RobotsTxtChecker.CheckResult result = checker.check(BASE_URL, "/news");

            // Then
            assertThat(result).isEqualTo(RobotsTxtChecker.CheckResult.BLOCKED_BY_ROBOTS);
            assertThat(checker.isAllowed(BASE_URL, "/news")).isFalse();
        }

        @Test
        @DisplayName("baseUrl이 /로 끝나도 Disallow: /news이면 /news는 BLOCKED_BY_ROBOTS다")
        void check_disallowedPathWithTrailingSlash_blocked() {
            // Given: /news만 막는 robots.txt
            RobotsTxtChecker checker = checkerReturning(DISALLOW_NEWS);

            // When
            RobotsTxtChecker.CheckResult result = checker.check(BASE_URL + "/", "/news");

            // Then
            assertThat(result).isEqualTo(RobotsTxtChecker.CheckResult.BLOCKED_BY_ROBOTS);
            assertThat(checker.isAllowed(BASE_URL + "/", "/news")).isFalse();
        }

        @Test
        @DisplayName("Disallow: /news이면 다른 경로 /blog/는 ALLOWED다")
        void check_otherPath_allowed() {
            // Given: /news만 막는 robots.txt
            RobotsTxtChecker checker = checkerReturning(DISALLOW_NEWS);

            // When
            RobotsTxtChecker.CheckResult result = checker.check(BASE_URL, "/blog/");

            // Then
            assertThat(result).isEqualTo(RobotsTxtChecker.CheckResult.ALLOWED);
        }

        @Test
        @DisplayName("빈 robots.txt는 BLOCKED_BY_ROBOTS다")
        void check_emptyRobotsTxt_blocked() {
            // Given: 본문이 빈 robots.txt
            RobotsTxtChecker checker = checkerReturning("");

            // When
            RobotsTxtChecker.CheckResult result = checker.check(BASE_URL, "/news");

            // Then
            assertThat(result).isEqualTo(RobotsTxtChecker.CheckResult.BLOCKED_BY_ROBOTS);
        }

        @Test
        @DisplayName("robots.txt를 가져오지 못하면 FETCH_FAILED다")
        void check_fetchError_fetchFailed() {
            // Given: 요청이 예외로 끝나는 WebClient
            WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IllegalStateException("connection refused")));
            RobotsTxtChecker checker = new RobotsTxtChecker(builder);

            // When
            RobotsTxtChecker.CheckResult result = checker.check(BASE_URL, "/news");

            // Then
            assertThat(result).isEqualTo(RobotsTxtChecker.CheckResult.FETCH_FAILED);
        }

        @Test
        @DisplayName("robots.txt 요청 URL은 baseUrl 끝 / 유무와 상관없이 {baseUrl}/robots.txt다")
        void check_robotsTxtUrl_sameWithOrWithoutTrailingSlash() {
            // Given
            RobotsTxtChecker checker = checkerReturning(DISALLOW_NEWS);

            // When
            checker.check(BASE_URL, "/news");
            checker.check(BASE_URL + "/", "/news");

            // Then
            assertThat(requestedUrls).containsExactly(
                "https://www.anthropic.com/robots.txt",
                "https://www.anthropic.com/robots.txt");
        }

        @Test
        @DisplayName("Disallow: /이면 지금처럼 BLOCKED_BY_ROBOTS다")
        void check_disallowAll_blocked() {
            // Given: 전체를 막는 robots.txt
            RobotsTxtChecker checker = checkerReturning("User-agent: *\nDisallow: /\n");

            // When
            RobotsTxtChecker.CheckResult result = checker.check(BASE_URL, "/news");

            // Then
            assertThat(result).isEqualTo(RobotsTxtChecker.CheckResult.BLOCKED_BY_ROBOTS);
        }

        @Test
        @DisplayName("Allow: /이면 지금처럼 /news는 ALLOWED다")
        void check_allowAll_allowed() {
            // Given: 현재 anthropic.com 형태의 전체 허용 robots.txt
            RobotsTxtChecker checker = checkerReturning("User-agent: *\nAllow: /\n");

            // When
            RobotsTxtChecker.CheckResult result = checker.check(BASE_URL, "/news");

            // Then
            assertThat(result).isEqualTo(RobotsTxtChecker.CheckResult.ALLOWED);
        }
    }
}
