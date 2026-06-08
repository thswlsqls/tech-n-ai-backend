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

/**
 * xAI News 페이지 스크래퍼
 * Next.js SSR + Cloudflare 보호 - Selenium 필수
 */
@Component
@Slf4j
public class XaiNewsScraper implements TechBlogScraper {

    private final WebClient.Builder webClientBuilder;
    private final RobotsTxtChecker robotsTxtChecker;
    private final ScraperProperties properties;
    private final RetryRegistry retryRegistry;
    private final Optional<WebDriver> webDriver;
    private final StructuredDataDateExtractor dateExtractor;

    private static final String NEWS_PATH = "/news";
    private static final String BASE_URL_KEY = "xai-news";
    private static final String PROVIDER_NAME = "XAI";

    public XaiNewsScraper(
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
            throw new ScrapingException("xAI News scraper source configuration not found");
        }

        if (!robotsTxtChecker.isAllowed(config.getBaseUrl(), NEWS_PATH)) {
            throw new ScrapingException("Scraping not allowed by robots.txt for xAI News");
        }

        Retry retry = retryRegistry.retry("scraperRetry");

        return retry.executeSupplier(() -> {
            try {
                // Cloudflare 보호로 Selenium 필수
                String html = fetchWithSelenium(config.getBaseUrl() + NEWS_PATH);

                if (html == null || html.isEmpty()) {
                    throw new ScrapingException("Empty HTML content from xAI News");
                }

                Document doc = Jsoup.parse(html);
                List<ScrapedTechArticle> articles = new ArrayList<>();

                var articleElements = doc.select("a[href*=/news/]");

                for (Element element : articleElements) {
                    try {
                        ScrapedTechArticle article = extractArticle(element, config.getBaseUrl());
                        if (article != null) {
                            articles.add(article);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract xAI article: {}", e.getMessage());
                    }
                }

                log.info("Scraped {} articles from xAI News", articles.size());
                return articles;
            } catch (Exception e) {
                log.error("xAI News scraping failed", e);
                throw new ScrapingException("xAI News scraping failed", e);
            }
        });
    }

    private String fetchWithSelenium(String url) {
        WebDriver driver = webDriver.orElseThrow(() ->
            new ScrapingException("Selenium WebDriver not available for xAI News (Cloudflare requires browser)"));

        try {
            driver.get(url);

            // Cloudflare 챌린지 통과 대기
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(d -> ((JavascriptExecutor) d)
                .executeScript("return document.readyState").equals("complete"));

            Thread.sleep(5000);

            return driver.getPageSource();
        } catch (Exception e) {
            log.error("Selenium fetch failed for xAI News: {}", url, e);
            throw new ScrapingException("Selenium fetch failed for xAI News", e);
        }
    }

    private ScrapedTechArticle extractArticle(Element element, String baseUrl) {
        String href = element.attr("href");
        if (href.equals("/news") || href.equals("/news/")) return null;

        String url = href.startsWith("http") ? href : baseUrl + href;

        // Tailwind CSS 기반 구조에서 타이틀 추출
        String title = ScraperDomUtils.extractText(element, "h3, h4");
        if (title == null || title.isEmpty()) {
            title = element.text();
        }
        if (title == null || title.isEmpty()) return null;

        Element parent = element.parent();
        String summary = ScraperDomUtils.extractText(parent, "p.text-secondary");
        String dateText = ScraperDomUtils.extractText(parent, "p.mono-tag, span.mono-tag");
        LocalDateTime publishedDate = DateParsingUtils.parseDateText(dateText);

        // 최종 fallback: 기사 상세 페이지에서 구조화 데이터 추출
        if (publishedDate == null) {
            publishedDate = dateExtractor.extractFromArticlePage(url);
        }

        String category = null;
        Element tagElement = parent != null ? parent.select("span.mono-tag").first() : null;
        if (tagElement != null) {
            category = tagElement.text();
        }

        return ScrapedTechArticle.builder()
            .title(title)
            .url(url)
            .summary(summary)
            .publishedDate(publishedDate)
            .author(null)
            .category(category)
            .providerName(PROVIDER_NAME)
            .build();
    }

    @Override
    public String getProviderName() {
        return "xAI";
    }

    @Override
    public String getBaseUrl() {
        ScraperProperties.ScraperSourceConfig config = properties.getSources().get(BASE_URL_KEY);
        return config != null ? config.getBaseUrl() : null;
    }
}
