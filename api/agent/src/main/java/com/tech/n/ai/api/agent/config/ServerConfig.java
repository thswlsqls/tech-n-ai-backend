package com.tech.n.ai.api.agent.config;

import com.tech.n.ai.client.feign.domain.oauth.config.OAuthFeignConfig;
import com.tech.n.ai.domain.aurora.config.ApiDomainConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan(
    basePackages = {
        "com.tech.n.ai.api.agent",
        "com.tech.n.ai.domain.aurora",
        "com.tech.n.ai.domain.mongodb",
        "com.tech.n.ai.client.feign",
        "com.tech.n.ai.client.rss",
        "com.tech.n.ai.client.slack",
        "com.tech.n.ai.client.scraper",
        "com.tech.n.ai.common.core",
        "com.tech.n.ai.common.exception",
        "com.tech.n.ai.common.conversation",
        "com.tech.n.ai.common.kafka"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = OAuthFeignConfig.class
    )
)
@Import({
    ApiDomainConfig.class
})
public class ServerConfig {
}
