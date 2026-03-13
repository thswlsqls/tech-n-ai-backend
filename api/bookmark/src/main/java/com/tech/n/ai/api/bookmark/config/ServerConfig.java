package com.tech.n.ai.api.bookmark.config;

import com.tech.n.ai.common.security.config.SecurityConfig;
import com.tech.n.ai.domain.aurora.config.ApiDomainConfig;
import com.tech.n.ai.domain.mongodb.config.MongoClientConfig;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Bookmark API 서버 설정
 */
@Configuration
@ComponentScan(basePackages = {
    "com.tech.n.ai.api.bookmark",
    "com.tech.n.ai.common.core",
    "com.tech.n.ai.common.exception",
    "com.tech.n.ai.common.security",
    "com.tech.n.ai.domain.aurora",
    "com.tech.n.ai.domain.mongodb"
})
@Import({
    ApiDomainConfig.class,
    SecurityConfig.class,
    MongoClientConfig.class,
})
@EnableConfigurationProperties(BookmarkConfig.class)
public class ServerConfig {
}
