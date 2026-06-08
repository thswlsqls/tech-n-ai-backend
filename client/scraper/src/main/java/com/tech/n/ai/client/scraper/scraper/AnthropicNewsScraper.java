package com.tech.n.ai.client.scraper.scraper;

import com.tech.n.ai.client.scraper.config.ScraperProperties;
import com.tech.n.ai.client.scraper.dto.ScrapedTechArticle;
import com.tech.n.ai.client.scraper.exception.ScrapingException;
import com.tech.n.ai.client.scraper.util.DateParsingUtils;
import com.tech.n.ai.client.scraper.util.RobotsTxtChecker;
import com.tech.n.ai.client.scraper.util.ScraperDomUtils;
import com.tech.n.ai.client.scraper.util.StructuredDataDateExtractor;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anthropic News 페이지 스크래퍼
 * Next.js RSC 페이지 - __next_f 스크립트에서 JSON 데이터 추출
 */
@Component
@Slf4j
public class AnthropicNewsScraper implements TechBlogScraper {

    private final WebClient.Builder webClientBuilder;
    private final RobotsTxtChecker robotsTxtChecker;
    private final ScraperProperties properties;
    private final RetryRegistry retryRegistry;
    private final StructuredDataDateExtractor dateExtractor;

    private static final String NEWS_PATH = "/news";
    private static final String BASE_URL_KEY = "anthropic-news";
    private static final String PROVIDER_NAME = "ANTHROPIC";

    // __next_f 스크립트에서 기사 블록(publishedOn + slug)을 추출하는 패턴
    private static final Pattern ARTICLE_BLOCK_PATTERN = Pattern.compile(
            "\"publishedOn\"\\s*:\\s*\"([^\"]+)\"[^}]*?" +
            "\"slug\"\\s*:\\s*\\{[^}]*\"current\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SUMMARY_PATTERN =
            Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]+)\"");

    public AnthropicNewsScraper(
            @Qualifier("scraperWebClientBuilder") WebClient.Builder webClientBuilder,
            RobotsTxtChecker robotsTxtChecker,
            ScraperProperties properties,
            RetryRegistry retryRegistry,
            StructuredDataDateExtractor dateExtractor) {
        this.webClientBuilder = webClientBuilder;
        this.robotsTxtChecker = robotsTxtChecker;
        this.properties = properties;
        this.retryRegistry = retryRegistry;
        this.dateExtractor = dateExtractor;
    }

    @Override
    public List<ScrapedTechArticle> scrapeArticles() {
        ScraperProperties.ScraperSourceConfig config = properties.getSources().get(BASE_URL_KEY);
        if (config == null) {
            throw new ScrapingException("Anthropic News scraper source configuration not found");
        }

        if (!robotsTxtChecker.isAllowed(config.getBaseUrl(), NEWS_PATH)) {
            throw new ScrapingException("Scraping not allowed by robots.txt for Anthropic News");
        }

        WebClient webClient = webClientBuilder.baseUrl(config.getBaseUrl()).build();
        Retry retry = retryRegistry.retry("scraperRetry");

        return retry.executeSupplier(() -> {
            try {
                log.debug("Scraping Anthropic News: {}", config.getBaseUrl() + NEWS_PATH);

                String html = webClient.get()
                    .uri(NEWS_PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

                if (html == null || html.isEmpty()) {
                    throw new ScrapingException("Empty HTML content from Anthropic News");
                }

                Document doc = Jsoup.parse(html);

                // __next_f 스크립트에서 slug → publishedOn 매핑 추출
                Map<String, ArticleData> slugDataMap = extractArticleDataFromNextData(doc);
                log.debug("Extracted {} article data entries from __next_f scripts", slugDataMap.size());

                List<ScrapedTechArticle> articles = new ArrayList<>();

                var articleElements = doc.select("a[href^=/news/]");

                for (Element element : articleElements) {
                    try {
                        ScrapedTechArticle article = extractArticle(element, config.getBaseUrl(), slugDataMap);
                        if (article != null) {
                            articles.add(article);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract Anthropic article: {}", e.getMessage());
                    }
                }

                log.info("Scraped {} articles from Anthropic News", articles.size());
                return articles;
            } catch (WebClientException e) {
                log.error("Failed to fetch Anthropic News", e);
                throw new ScrapingException("Anthropic News scraping failed", e);
            } catch (Exception e) {
                log.error("Anthropic News scraping failed", e);
                throw new ScrapingException("Anthropic News scraping failed", e);
            }
        });
    }

    /**
     * __next_f 스크립트 태그에서 기사 데이터(publishedOn, title, summary)를 slug 기준으로 추출.
     * Next.js RSC 데이터는 이중 이스케이프(\\"key\\":\\"value\\")되어 있으므로 unescape 후 파싱.
     */
    private Map<String, ArticleData> extractArticleDataFromNextData(Document doc) {
        Map<String, ArticleData> result = new HashMap<>();

        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String scriptContent = script.data();
            if (!scriptContent.contains("publishedOn")) {
                continue;
            }

            // Next.js RSC 이중 이스케이프 해제: \\" → " , \\/ → /
            String unescaped = scriptContent
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\_", "_");

            log.debug("Found __next_f script with publishedOn, unescaped length: {}", unescaped.length());

            // publishedOn → slug 순서로 기사 블록 파싱 (publishedOn이 slug 앞에 위치)
            // 패턴: "publishedOn":"2026-01-28T21:15:36.668Z","slug":{"_type":"slug","current":"some-slug"}
            Matcher blockMatcher = ARTICLE_BLOCK_PATTERN.matcher(unescaped);
            while (blockMatcher.find()) {
                String dateStr = blockMatcher.group(1);
                String slug = blockMatcher.group(2);

                // slug 위치 이후에서 title 추출
                int blockEnd = blockMatcher.end();
                String afterBlock = unescaped.substring(Math.max(0, blockMatcher.start() - 500),
                        Math.min(unescaped.length(), blockEnd + 500));

                Matcher titleMatcher = TITLE_PATTERN.matcher(afterBlock);
                String title = titleMatcher.find() ? titleMatcher.group(1) : null;

                Matcher summaryMatcher = SUMMARY_PATTERN.matcher(afterBlock);
                String summary = summaryMatcher.find() ? summaryMatcher.group(1) : null;

                result.put(slug, new ArticleData(dateStr, title, summary));
                log.debug("Mapped slug '{}' → publishedOn '{}', title '{}'", slug, dateStr, title);
            }
        }

        return result;
    }

    private ScrapedTechArticle extractArticle(Element element, String baseUrl,
                                               Map<String, ArticleData> slugDataMap) {
        String href = element.attr("href");
        String url = href.startsWith("http") ? href : baseUrl + href;

        // href에서 slug 추출: /news/some-slug → some-slug
        String slug = href.replaceFirst("^/news/", "").replaceAll("/$", "");

        // __next_f 데이터에서 날짜/제목/요약 조회
        ArticleData data = slugDataMap.get(slug);

        String title = null;
        String summary = null;
        LocalDateTime publishedDate = null;

        if (data != null) {
            publishedDate = DateParsingUtils.parseDateText(data.publishedOn);
            title = data.title;
            summary = data.summary;
        }

        // fallback: DOM에서 제목 추출
        if (title == null || title.isEmpty()) {
            title = ScraperDomUtils.extractText(element, "h2, h3, h4");
        }
        if (title == null || title.isEmpty()) return null;

        // fallback: DOM에서 요약/날짜 추출
        Element parent = element.parent();
        if (summary == null || summary.isEmpty()) {
            summary = ScraperDomUtils.extractText(parent, "p");
        }
        if (publishedDate == null) {
            String dateText = ScraperDomUtils.extractText(parent, "time, span, div");
            publishedDate = DateParsingUtils.parseDateText(dateText);
        }

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

    @Override
    public String getProviderName() {
        return "Anthropic";
    }

    @Override
    public String getBaseUrl() {
        ScraperProperties.ScraperSourceConfig config = properties.getSources().get(BASE_URL_KEY);
        return config != null ? config.getBaseUrl() : null;
    }

    private record ArticleData(String publishedOn, String title, String summary) {}
}
