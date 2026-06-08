package com.tech.n.ai.client.rss.parser;

import com.tech.n.ai.client.rss.config.RssProperties;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndEntry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.stream.Collectors;

/**
 * Google AI Blog RSS 피드 파서
 * RSS 2.0 형식의 Google AI Blog 피드를 파싱
 * 여러 카테고리를 콤마로 합치고, enclosure에서 이미지 URL을 추출
 */
@Component
public class GoogleAiBlogRssParser extends AbstractRssParser {

    public GoogleAiBlogRssParser(
            @Qualifier("rssWebClientBuilder") WebClient.Builder webClientBuilder,
            RssProperties properties,
            RetryRegistry retryRegistry) {
        super(webClientBuilder, properties, retryRegistry);
    }

    @Override
    protected String getSourceKey() {
        return "google-ai-blog";
    }

    @Override
    public String getSourceName() {
        return "Google AI Blog";
    }

    @Override
    protected String extractCategory(SyndEntry entry) {
        return entry.getCategories().isEmpty()
            ? null
            : entry.getCategories().stream()
                .map(SyndCategory::getName)
                .collect(Collectors.joining(","));
    }

    @Override
    protected String extractImageUrl(SyndEntry entry) {
        if (!entry.getEnclosures().isEmpty()) {
            return entry.getEnclosures().get(0).getUrl();
        }
        return null;
    }
}
