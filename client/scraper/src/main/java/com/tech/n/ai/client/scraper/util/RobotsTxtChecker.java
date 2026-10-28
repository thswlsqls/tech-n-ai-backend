package com.tech.n.ai.client.scraper.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;

/**
 * robots.txt 확인 유틸리티 클래스
 * crawler-commons를 사용하여 robots.txt를 파싱하고 스크래핑 허용 여부 확인
 */
@Component
@Slf4j
public class RobotsTxtChecker {

    private final WebClient.Builder webClientBuilder;

    public RobotsTxtChecker(@Qualifier("scraperWebClientBuilder") WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * robots.txt 확인 결과
     */
    @Getter
    public enum CheckResult {
        ALLOWED("허용"),
        BLOCKED_BY_ROBOTS("Robots.txt에 의해 차단됨"),
        FETCH_FAILED("사이트에 접속할 수 없습니다");

        private final String message;

        CheckResult(String message) {
            this.message = message;
        }
    }

    /**
     * robots.txt를 확인하여 특정 경로의 스크래핑이 허용되는지 확인
     *
     * @param baseUrl 기본 URL
     * @param path 확인할 경로
     * @return 스크래핑이 허용되면 true, 아니면 false
     */
    public boolean isAllowed(String baseUrl, String path) {
        return check(baseUrl, path) == CheckResult.ALLOWED;
    }

    /**
     * robots.txt 확인 결과를 상세하게 반환
     *
     * @param baseUrl 기본 URL
     * @param path 확인할 경로
     * @return 확인 결과 (ALLOWED, BLOCKED_BY_ROBOTS, FETCH_FAILED)
     */
    public CheckResult check(String baseUrl, String path) {
        try {
            String siteUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
            String robotsTxtUrl = siteUrl + "/robots.txt";

            log.debug("Checking robots.txt for {}: {}", baseUrl, robotsTxtUrl);

            String robotsTxt = webClientBuilder.build()
                .get()
                .uri(robotsTxtUrl)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (robotsTxt == null || robotsTxt.isEmpty()) {
                log.warn("Empty robots.txt for {}", baseUrl);
                return CheckResult.BLOCKED_BY_ROBOTS;
            }

            SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
            BaseRobotRules rules = parser.parseContent(robotsTxtUrl, robotsTxt.getBytes(), "text/plain", "ShrimpTM-Demo/1.0");

            // crawler-commons는 전체 URL을 받는다. 상대 경로를 넘기면 루트("/")로 검사한다.
            boolean allowed = rules.isAllowed(siteUrl + path);
            log.debug("robots.txt check for {}: path={}, allowed={}", baseUrl, path, allowed);

            return allowed ? CheckResult.ALLOWED : CheckResult.BLOCKED_BY_ROBOTS;
        } catch (Exception e) {
            log.warn("Failed to check robots.txt for {}: {}", baseUrl, e.getMessage());
            return CheckResult.FETCH_FAILED;
        }
    }
}
