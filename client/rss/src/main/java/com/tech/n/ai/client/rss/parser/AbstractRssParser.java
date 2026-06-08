package com.tech.n.ai.client.rss.parser;

import com.tech.n.ai.client.rss.config.RssProperties;
import com.tech.n.ai.client.rss.dto.RssFeedItem;
import com.tech.n.ai.client.rss.exception.RssParsingException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RSS 피드 파서의 공통 골격 (Template Method).
 * 피드 fetch + 재시도 + 파싱 흐름을 한곳에 두고,
 * 소스마다 달라지는 지점만 하위 클래스가 채운다.
 */
public abstract class AbstractRssParser implements RssParser {

    // 구체 클래스 기준으로 로거를 만들어, 로그 카테고리가 소스별로 남도록 한다.
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final WebClient.Builder webClientBuilder;
    private final RssProperties properties;
    private final RetryRegistry retryRegistry;

    protected AbstractRssParser(
            WebClient.Builder webClientBuilder,
            RssProperties properties,
            RetryRegistry retryRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.retryRegistry = retryRegistry;
    }

    /**
     * application.yml의 rss.sources 맵에서 이 소스를 찾는 키 (예: "ars-technica")
     */
    protected abstract String getSourceKey();

    @Override
    public List<RssFeedItem> parse() {
        String sourceName = getSourceName();
        RssProperties.RssSourceConfig config = properties.getSources().get(getSourceKey());
        if (config == null) {
            throw new RssParsingException(sourceName + " RSS source configuration not found");
        }

        WebClient webClient = webClientBuilder.baseUrl(config.getFeedUrl()).build();
        Retry retry = retryRegistry.retry("rssRetry");

        return retry.executeSupplier(() -> {
            try {
                log.debug("Fetching {} RSS feed from: {}", sourceName, config.getFeedUrl());
                String feedContent = webClient.get()
                    .uri("")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

                if (feedContent == null || feedContent.isEmpty()) {
                    throw new RssParsingException("Empty RSS feed content received from " + sourceName);
                }

                feedContent = prepareContent(feedContent);

                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new StringReader(feedContent));

                log.debug("Successfully parsed {} RSS feed. Found {} entries", sourceName, feed.getEntries().size());

                return feed.getEntries().stream()
                    .map(this::convertToRssFeedItem)
                    .collect(Collectors.toList());
            } catch (WebClientException e) {
                log.error("Failed to fetch {} RSS feed", sourceName, e);
                throw new RssParsingException(sourceName + " RSS feed fetch failed", e);
            } catch (Exception e) {
                log.error("Failed to parse {} RSS feed", sourceName, e);
                throw new RssParsingException(sourceName + " RSS parsing failed", e);
            }
        });
    }

    /**
     * fetch한 원문을 파싱 직전에 다듬는다. 기본: BOM 제거 + 앞뒤 공백 제거.
     */
    protected String prepareContent(String content) {
        return removeBOM(content).trim();
    }

    /**
     * UTF-8 BOM(U+FEFF)이 문자열 시작에 있으면 제거.
     */
    protected String removeBOM(String content) {
        if (content != null && content.startsWith("\uFEFF")) {
            return content.substring(1);
        }
        return content;
    }

    /**
     * SyndEntry를 RssFeedItem으로 변환.
     * 발행일이 없는 경우 null로 유지 (데이터 무결성 보장).
     */
    protected RssFeedItem convertToRssFeedItem(SyndEntry entry) {
        LocalDateTime publishedDate = null;
        if (entry.getPublishedDate() != null) {
            publishedDate = LocalDateTime.ofInstant(
                entry.getPublishedDate().toInstant(),
                ZoneId.systemDefault()
            );
        } else if (entry.getUpdatedDate() != null) {
            publishedDate = LocalDateTime.ofInstant(
                entry.getUpdatedDate().toInstant(),
                ZoneId.systemDefault()
            );
        }

        String description = entry.getDescription() != null
            ? entry.getDescription().getValue()
            : null;

        String guid = entry.getUri() != null
            ? entry.getUri()
            : entry.getLink();

        return RssFeedItem.builder()
            .title(entry.getTitle())
            .link(entry.getLink())
            .description(description)
            .publishedDate(publishedDate)
            .author(entry.getAuthor())
            .category(extractCategory(entry))
            .guid(guid)
            .imageUrl(extractImageUrl(entry))
            .build();
    }

    /**
     * 카테고리 추출. 기본: 첫 번째 카테고리 이름, 없으면 null.
     */
    protected String extractCategory(SyndEntry entry) {
        return entry.getCategories().isEmpty()
            ? null
            : entry.getCategories().get(0).getName();
    }

    /**
     * 이미지 URL 추출. 기본: 없음(null).
     */
    protected String extractImageUrl(SyndEntry entry) {
        return null;
    }

    @Override
    public String getFeedUrl() {
        RssProperties.RssSourceConfig config = properties.getSources().get(getSourceKey());
        return config != null ? config.getFeedUrl() : null;
    }
}
