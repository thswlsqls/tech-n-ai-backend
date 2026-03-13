package com.tech.n.ai.batch.source.config;

// Feign Config Imports
import com.tech.n.ai.client.feign.config.EmergingTechInternalFeignConfig;
import com.tech.n.ai.client.feign.config.GitHubFeignConfig;
import com.tech.n.ai.client.feign.config.OpenFeignConfig;
import com.tech.n.ai.client.feign.domain.oauth.config.OAuthFeignConfig;

// Domain Config Import
import com.tech.n.ai.domain.aurora.config.BatchDomainConfig;
import com.tech.n.ai.domain.mongodb.config.MongoClientConfig;
import com.tech.n.ai.domain.mongodb.config.MongoIndexConfig;
import com.tech.n.ai.domain.mongodb.config.VectorSearchIndexConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration
@ComponentScan(basePackages = {
    "com.tech.n.ai.batch.source",
    "com.tech.n.ai.domain.aurora",
    "com.tech.n.ai.domain.mongodb",
    "com.tech.n.ai.client.scraper",
    "com.tech.n.ai.client.feign",
    "com.tech.n.ai.client.rss"
})
@Import({
    // Domain Config
    BatchDomainConfig.class,
    MongoClientConfig.class,
    MongoIndexConfig.class,
    VectorSearchIndexConfig.class,

    // Feign Configs
    OpenFeignConfig.class,
    EmergingTechInternalFeignConfig.class,
    OAuthFeignConfig.class,
    GitHubFeignConfig.class
})
public class ServerConfig {

}
