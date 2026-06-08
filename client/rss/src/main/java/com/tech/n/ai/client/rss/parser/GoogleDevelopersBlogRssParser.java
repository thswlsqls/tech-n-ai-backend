package com.tech.n.ai.client.rss.parser;

import com.tech.n.ai.client.rss.config.RssProperties;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Google Developers Blog RSS 피드 파서
 * Atom 1.0 형식의 Google Developers Blog 피드를 파싱
 * Rome 라이브러리가 Atom 1.0과 RSS 2.0을 모두 SyndEntry로 추상화하므로 공통 변환 로직을 사용
 */
@Component
public class GoogleDevelopersBlogRssParser extends AbstractRssParser {

    public GoogleDevelopersBlogRssParser(
            @Qualifier("rssWebClientBuilder") WebClient.Builder webClientBuilder,
            RssProperties properties,
            RetryRegistry retryRegistry) {
        super(webClientBuilder, properties, retryRegistry);
    }

    @Override
    protected String getSourceKey() {
        return "google-developers-blog";
    }

    @Override
    public String getSourceName() {
        return "Google Developers Blog";
    }
}
