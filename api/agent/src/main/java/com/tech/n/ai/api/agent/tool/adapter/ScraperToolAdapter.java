package com.tech.n.ai.api.agent.tool.adapter;

import com.tech.n.ai.api.agent.tool.dto.ScrapedContentDto;
import com.tech.n.ai.api.agent.tool.util.TextTruncator;
import com.tech.n.ai.client.scraper.util.RobotsTxtChecker;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;

/**
 * 웹 스크래핑을 LangChain4j Tool 형식으로 래핑하는 어댑터
 * WebClient + Jsoup 기반 범용 웹 페이지 크롤링
 */
@Slf4j
@Component
public class ScraperToolAdapter {

    private final RobotsTxtChecker robotsTxtChecker;
    private final WebClient webClient;

    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "EmergingTechAgent/1.0";

    public ScraperToolAdapter(
            RobotsTxtChecker robotsTxtChecker,
            @Qualifier("scraperWebClientBuilder") WebClient.Builder webClientBuilder) {
        this.robotsTxtChecker = robotsTxtChecker;
        this.webClient = webClientBuilder.build();
    }

    /**
     * 웹 페이지 스크래핑
     *
     * @param url 크롤링할 URL
     * @return 스크래핑 결과
     */
    public ScrapedContentDto scrape(String url) {
        try {
            URI uri = URI.create(url);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            String path = uri.getPath();

            RobotsTxtChecker.CheckResult checkResult = robotsTxtChecker.check(baseUrl, path);
            if (checkResult != RobotsTxtChecker.CheckResult.ALLOWED) {
                log.warn("크롤링 차단: {} (사유: {})", url, checkResult.getMessage());
                return new ScrapedContentDto(null, checkResult.getMessage(), url);
            }

            String html = webClient.get()
                .uri(url)
                .header("User-Agent", USER_AGENT)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            if (html == null || html.isEmpty()) {
                return new ScrapedContentDto(null, "페이지 내용을 가져올 수 없습니다.", url);
            }

            Document doc = Jsoup.parse(html);
            String title = doc.title();
            String content = extractContent(doc);

            return new ScrapedContentDto(
                title,
                TextTruncator.truncate(content, MAX_CONTENT_LENGTH),
                url
            );
        } catch (Exception e) {
            log.error("웹 페이지 크롤링 실패: {}", url, e);
            return new ScrapedContentDto(null, "크롤링 실패: " + e.getMessage(), url);
        }
    }

    private String extractContent(Document doc) {
        String[] selectors = {"article", "main", ".content", ".post-content", "[role=main]"};

        for (String selector : selectors) {
            String text = doc.select(selector).text();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }

        return doc.body() != null ? doc.body().text() : "";
    }
}
