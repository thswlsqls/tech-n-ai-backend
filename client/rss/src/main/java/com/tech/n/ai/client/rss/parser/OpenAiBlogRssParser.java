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
 * OpenAI Blog RSS 피드 파서
 * RSS 2.0 형식의 OpenAI Blog 피드를 파싱
 * 여러 카테고리를 콤마로 합쳐 사용
 */
@Component
public class OpenAiBlogRssParser extends AbstractRssParser {

    public OpenAiBlogRssParser(
            @Qualifier("rssWebClientBuilder") WebClient.Builder webClientBuilder,
            RssProperties properties,
            RetryRegistry retryRegistry) {
        super(webClientBuilder, properties, retryRegistry);
    }

    @Override
    protected String getSourceKey() {
        return "openai-blog";
    }

    @Override
    public String getSourceName() {
        return "OpenAI Blog";
    }

    @Override
    protected String extractCategory(SyndEntry entry) {
        return entry.getCategories().isEmpty()
            ? null
            : entry.getCategories().stream()
                .map(SyndCategory::getName)
                .collect(Collectors.joining(","));
    }
}
