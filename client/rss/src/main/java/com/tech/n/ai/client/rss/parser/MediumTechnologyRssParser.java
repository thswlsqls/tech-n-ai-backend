package com.tech.n.ai.client.rss.parser;

import com.tech.n.ai.client.rss.config.RssProperties;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Medium Technology RSS 피드 파서
 * RSS 2.0 형식의 Medium Technology 태그 피드를 파싱
 */
@Component
public class MediumTechnologyRssParser extends AbstractRssParser {

    public MediumTechnologyRssParser(
            @Qualifier("rssWebClientBuilder") WebClient.Builder webClientBuilder,
            RssProperties properties,
            RetryRegistry retryRegistry) {
        super(webClientBuilder, properties, retryRegistry);
    }

    @Override
    protected String getSourceKey() {
        return "medium-technology";
    }

    @Override
    public String getSourceName() {
        return "Medium Technology";
    }
}
