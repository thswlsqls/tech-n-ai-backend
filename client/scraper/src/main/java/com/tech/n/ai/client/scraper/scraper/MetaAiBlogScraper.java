package com.tech.n.ai.client.scraper.scraper;

import com.tech.n.ai.client.scraper.config.ScraperProperties;
import com.tech.n.ai.client.scraper.dto.ScrapedTechArticle;
import com.tech.n.ai.client.scraper.exception.ScrapingException;
import com.tech.n.ai.client.scraper.util.RobotsTxtChecker;
import com.tech.n.ai.client.scraper.util.ScraperDomUtils;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.tech.n.ai.client.scraper.util.DateParsingUtils;
import com.tech.n.ai.client.scraper.util.StructuredDataDateExtractor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Meta AI Blog 페이지 스크래퍼
 * React SPA - Selenium 사용 가능 시 SPA 렌더링, 아니면 WebClient fallback
 */
@Component
@Slf4j
public class MetaAiBlogScraper implements TechBlogScraper {

    private final WebClient.Builder webClientBuilder;
    private final RobotsTxtChecker robotsTxtChecker;
    private final ScraperProperties properties;
    private final RetryRegistry retryRegistry;
    private final Optional<WebDriver> webDriver;
    private final StructuredDataDateExtractor dateExtractor;

    private static final String BLOG_PATH = "/blog/";
    private static final String BASE_URL_KEY = "meta-ai-blog";
    private static final String PROVIDER_NAME = "META";

    // 날짜 패턴: "December 16, 2025" 또는 "Dec 18, 2025" 형태
    private static final Pattern DATE_TEXT_PATTERN =
            Pattern.compile("(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{1,2},\\s+\\d{4}");

    public MetaAiBlogScraper(
            @Qualifier("scraperWebClientBuilder") WebClient.Builder webClientBuilder,
            RobotsTxtChecker robotsTxtChecker,
            ScraperProperties properties,
            RetryRegistry retryRegistry,
            Optional<WebDriver> webDriver,
            StructuredDataDateExtractor dateExtractor) {
        this.webClientBuilder = webClientBuilder;
        this.robotsTxtChecker = robotsTxtChecker;
        this.properties = properties;
        this.retryRegistry = retryRegistry;
        this.webDriver = webDriver;
        this.dateExtractor = dateExtractor;
    }

    @Override
    public List<ScrapedTechArticle> scrapeArticles() {
        ScraperProperties.ScraperSourceConfig config = properties.getSources().get(BASE_URL_KEY);
        if (config == null) {
            throw new ScrapingException("Meta AI Blog scraper source configuration not found");
        }

        if (!robotsTxtChecker.isAllowed(config.getBaseUrl(), BLOG_PATH)) {
            throw new ScrapingException("Scraping not allowed by robots.txt for Meta AI Blog");
        }

        Retry retry = retryRegistry.retry("scraperRetry");

        return retry.executeSupplier(() -> {
            try {
                String html;

                if (config.isRequiresSelenium() && webDriver.isPresent()) {
                    html = fetchWithSelenium(config.getBaseUrl() + BLOG_PATH);
                } else {
                    html = fetchWithWebClient(config.getBaseUrl(), BLOG_PATH);
                }

                if (html == null || html.isEmpty()) {
                    throw new ScrapingException("Empty HTML content from Meta AI Blog");
                }

                Document doc = Jsoup.parse(html);
                List<ScrapedTechArticle> articles = new ArrayList<>();

                var articleElements = doc.select("a[href*=/blog/]");

                for (Element element : articleElements) {
                    try {
                        ScrapedTechArticle article = extractArticle(element, config.getBaseUrl());
                        if (article != null) {
                            articles.add(article);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract Meta AI article: {}", e.getMessage());
                    }
                }

                log.info("Scraped {} articles from Meta AI Blog", articles.size());
                return articles;
            } catch (Exception e) {
                log.error("Meta AI Blog scraping failed", e);
                throw new ScrapingException("Meta AI Blog scraping failed", e);
            }
        });
    }

    private String fetchWithSelenium(String url) {
        WebDriver driver = webDriver.orElseThrow(() ->
            new ScrapingException("Selenium WebDriver not available for Meta AI Blog"));

        try {
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(d -> ((JavascriptExecutor) d)
                .executeScript("return document.readyState").equals("complete"));

            Thread.sleep(3000);

            return driver.getPageSource();
        } catch (Exception e) {
            log.error("Selenium fetch failed for: {}", url, e);
            throw new ScrapingException("Selenium fetch failed for Meta AI Blog", e);
        }
    }

    private String fetchWithWebClient(String baseUrl, String path) {
        WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();
        return webClient.get()
            .uri(path)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }

    private ScrapedTechArticle extractArticle(Element element, String baseUrl) {
        String href = element.attr("href");

        // 블로그 인덱스, 페이지네이션, 쿼리 파라미터 포함 링크 필터링
        if (href.equals("/blog/") || href.equals("/blog")) return null;
        if (href.contains("?")) return null;

        // /blog/ 뒤에 실제 slug가 있는지 확인 (개별 기사 URL만 허용)
        String pathAfterBlog = href.replaceFirst(".*?/blog/", "").replaceAll("/$", "");
        if (pathAfterBlog.isEmpty()) return null;

        String url = href.startsWith("http") ? href : baseUrl + href;

        Element parent = element.parent();
        String title = ScraperDomUtils.extractText(element, "h2, h3, h4");
        if (title == null || title.isEmpty()) {
            title = element.text();
        }
        if (title == null || title.isEmpty()) return null;

        String summary = ScraperDomUtils.extractText(parent, "p, div:not(:has(*))");

        // 날짜 추출: CSS 셀렉터 → 부모 요소 텍스트에서 정규식 매칭 순으로 시도
        LocalDateTime publishedDate = extractPublishedDate(parent);

        // 최종 fallback: 기사 상세 페이지에서 구조화 데이터 추출
        if (publishedDate == null) {
            publishedDate = dateExtractor.extractFromArticlePage(url);
        }

        return ScrapedTechArticle.builder()
            .title(title)
            .url(url)
            .summary(summary)
            .publishedDate(publishedDate)
            .author(null)
            .category(null)
            .providerName(PROVIDER_NAME)
            .build();
    }

    /**
     * 날짜 추출: CSS 셀렉터 → 조상 요소 텍스트에서 정규식 매칭
     */
    private LocalDateTime extractPublishedDate(Element context) {
        if (context == null) return null;

        // 1차: CSS 셀렉터로 시도 (time, span, div)
        String dateText = ScraperDomUtils.extractText(context, "time, span, div");
        LocalDateTime result = DateParsingUtils.parseDateText(dateText);
        if (result != null) return result;

        // 2차: 현재 요소와 조상 요소의 텍스트에서 날짜 패턴 정규식 매칭
        Element current = context;
        for (int i = 0; i < 3 && current != null; i++) {
            String text = current.text();
            Matcher matcher = DATE_TEXT_PATTERN.matcher(text);
            if (matcher.find()) {
                result = DateParsingUtils.parseDateText(matcher.group());
                if (result != null) return result;
            }
            current = current.parent();
        }

        return null;
    }

    @Override
    public String getProviderName() {
        return "Meta AI";
    }

    @Override
    public String getBaseUrl() {
        ScraperProperties.ScraperSourceConfig config = properties.getSources().get(BASE_URL_KEY);
        return config != null ? config.getBaseUrl() : null;
    }
}
