package com.tech.n.ai.client.scraper.scraper;

import com.tech.n.ai.client.scraper.config.ScraperProperties;
import com.tech.n.ai.client.scraper.dto.ScrapedContestItem;
import com.tech.n.ai.client.scraper.exception.ScrapingException;
import com.tech.n.ai.client.scraper.util.RobotsTxtChecker;
import com.tech.n.ai.client.scraper.util.ScraperDomUtils;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * LeetCode Contests 웹 스크래퍼
 * GraphQL 엔드포인트 우선 시도, 실패 시 HTML 스크래핑 대안
 */
@Component
@Slf4j
public class LeetCodeScraper implements WebScraper {
    
    private final WebClient.Builder webClientBuilder;
    private final RobotsTxtChecker robotsTxtChecker;
    private final ScraperProperties properties;
    private final RetryRegistry retryRegistry;
    
    public LeetCodeScraper(
            @Qualifier("scraperWebClientBuilder") WebClient.Builder webClientBuilder,
            RobotsTxtChecker robotsTxtChecker,
            ScraperProperties properties,
            RetryRegistry retryRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.robotsTxtChecker = robotsTxtChecker;
        this.properties = properties;
        this.retryRegistry = retryRegistry;
    }
    
    private static final String GRAPHQL_ENDPOINT = "/graphql";
    private static final String CONTESTS_PAGE = "/contest/";
    
    @Override
    public List<ScrapedContestItem> scrape() {
        ScraperProperties.ScraperSourceConfig config = properties.getSources().get("leetcode");
        if (config == null) {
            throw new ScrapingException("LeetCode scraper source configuration not found");
        }
        
        // robots.txt 확인
        if (!robotsTxtChecker.isAllowed(config.getBaseUrl(), CONTESTS_PAGE)) {
            throw new ScrapingException("Scraping not allowed by robots.txt for LeetCode");
        }
        
        WebClient webClient = webClientBuilder.baseUrl(config.getBaseUrl()).build();
        Retry retry = retryRegistry.retry("scraperRetry");
        
        return retry.executeSupplier(() -> {
            try {
                // GraphQL 엔드포인트 우선 시도
                List<ScrapedContestItem> items = tryGraphQL(webClient, config);
                
                if (items != null && !items.isEmpty()) {
                    log.debug("Successfully scraped LeetCode contests via GraphQL. Found {} items", items.size());
                    return items;
                }
                
                // GraphQL 실패 시 HTML 스크래핑 대안
                log.debug("GraphQL failed, falling back to HTML scraping");
                return scrapeHtml(webClient, config);
            } catch (WebClientException e) {
                log.error("Failed to fetch LeetCode contests", e);
                throw new ScrapingException("LeetCode scraping failed", e);
            } catch (Exception e) {
                log.error("Failed to scrape LeetCode contests", e);
                throw new ScrapingException("LeetCode scraping failed", e);
            }
        });
    }
    
    /**
     * GraphQL 엔드포인트를 통해 대회 정보 조회 시도
     */
    private List<ScrapedContestItem> tryGraphQL(WebClient webClient, ScraperProperties.ScraperSourceConfig config) {
        try {
            // GraphQL 쿼리 구성 (공식 문서 없음, 일반적인 구조 사용)
            String graphQLQuery = """
                {
                  "query": "query { allContests { title slug startTime duration } }"
                }
                """;
            
            log.debug("Attempting GraphQL query to LeetCode");
            
            String response = webClient.post()
                .uri(GRAPHQL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(graphQLQuery)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            if (response == null || response.isEmpty()) {
                log.warn("Empty GraphQL response from LeetCode");
                return null;
            }
            
            // GraphQL 응답 파싱 (간단한 JSON 파싱)
            // 실제 구현에서는 JSON 라이브러리 사용 권장
            log.debug("GraphQL response received, but parsing not fully implemented");
            // TODO: GraphQL 응답 파싱 구현 (JSON 구조에 따라)
            
            return null; // GraphQL 파싱 미구현으로 null 반환하여 HTML 대안 사용
        } catch (Exception e) {
            log.debug("GraphQL request failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * HTML 스크래핑을 통해 대회 정보 조회
     */
    private List<ScrapedContestItem> scrapeHtml(WebClient webClient, ScraperProperties.ScraperSourceConfig config) {
        try {
            log.debug("Scraping LeetCode contests page: {}", config.getBaseUrl() + CONTESTS_PAGE);
            
            String html = webClient.get()
                .uri(CONTESTS_PAGE)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            if (html == null || html.isEmpty()) {
                throw new ScrapingException("Empty HTML content received from LeetCode");
            }
            
            Document doc = Jsoup.parse(html);
            List<ScrapedContestItem> items = new ArrayList<>();
            
            // LeetCode contests 페이지 구조에 맞는 선택자 사용
            // 실제 구조에 따라 선택자 조정 필요
            var contestElements = doc.select(".contest-card, .contest-item, [data-contest-id]");
            
            if (contestElements.isEmpty()) {
                // 대안 선택자 시도
                contestElements = doc.select("a[href*='/contest/']");
            }
            
            for (Element element : contestElements) {
                try {
                    ScrapedContestItem item = extractContestInfo(element, config.getBaseUrl());
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract contest info from element: {}", e.getMessage());
                }
            }
            
            log.debug("Successfully scraped {} contests from LeetCode HTML", items.size());
            return items;
        } catch (Exception e) {
            log.error("HTML scraping failed for LeetCode", e);
            throw new ScrapingException("LeetCode HTML scraping failed", e);
        }
    }
    
    /**
     * HTML 요소에서 대회 정보 추출
     */
    private ScrapedContestItem extractContestInfo(Element element, String baseUrl) {
        try {
            String title = ScraperDomUtils.extractText(element, "h3, h4, .title, .contest-title");
            if (title == null) {
                title = element.text();
            }
            
            String url = element.attr("href");
            if (url != null && !url.isEmpty() && !url.startsWith("http")) {
                url = baseUrl + (url.startsWith("/") ? url : "/" + url);
            } else if (url == null || url.isEmpty()) {
                url = baseUrl + CONTESTS_PAGE;
            }
            
            String description = ScraperDomUtils.extractText(element, ".description, .contest-description");
            
            // 날짜 파싱 (실제 구조에 따라 조정 필요)
            LocalDateTime startDate = null;
            LocalDateTime endDate = null;
            Element dateElement = element.select(".date, .time, .start-time").first();
            if (dateElement != null) {
                try {
                    String dateText = dateElement.text();
                    // 간단한 날짜 파싱 (실제 형식에 맞게 조정 필요)
                    // startDate = LocalDateTime.parse(dateText, DateTimeFormatter.ISO_DATE_TIME);
                } catch (Exception e) {
                    log.debug("Failed to parse date: {}", e.getMessage());
                }
            }
            
            String organizer = "LeetCode";
            String location = "Online";
            String category = "Algorithm Contest";
            
            return ScrapedContestItem.builder()
                .title(title)
                .url(url)
                .description(description)
                .startDate(startDate)
                .endDate(endDate)
                .organizer(organizer)
                .location(location)
                .category(category)
                .prize(null)
                .imageUrl(null)
                .build();
        } catch (Exception e) {
            log.warn("Failed to extract contest info: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public String getSourceName() {
        return "LeetCode Contests";
    }
    
    @Override
    public String getBaseUrl() {
        ScraperProperties.ScraperSourceConfig config = properties.getSources().get("leetcode");
        return config != null ? config.getBaseUrl() : null;
    }
}
