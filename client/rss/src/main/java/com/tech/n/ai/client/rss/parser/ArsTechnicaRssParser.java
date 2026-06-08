package com.tech.n.ai.client.rss.parser;

import com.tech.n.ai.client.rss.config.RssProperties;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Ars Technica RSS 피드 파서
 * RSS 2.0 형식의 Ars Technica 피드를 파싱
 */
@Component
public class ArsTechnicaRssParser extends AbstractRssParser {

    public ArsTechnicaRssParser(
            @Qualifier("rssWebClientBuilder") WebClient.Builder webClientBuilder,
            RssProperties properties,
            RetryRegistry retryRegistry) {
        super(webClientBuilder, properties, retryRegistry);
    }

    @Override
    protected String getSourceKey() {
        return "ars-technica";
    }

    @Override
    public String getSourceName() {
        return "Ars Technica";
    }
}
