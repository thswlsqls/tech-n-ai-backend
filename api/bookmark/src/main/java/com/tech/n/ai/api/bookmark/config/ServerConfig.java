package com.tech.n.ai.api.bookmark.config;

import com.tech.n.ai.common.security.config.SecurityConfig;
import com.tech.n.ai.domain.aurora.config.ApiDomainConfig;
import com.tech.n.ai.domain.mongodb.config.MongoClientConfig;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.ZoneId;

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

    /**
     * 집계 날짜를 자를 때 쓰는 시계.
     *
     * 배포 컨테이너의 JVM 기본 존이 KST 라는 보장이 없어서 존을 여기서 못 박는다.
     * 테스트는 이 빈 대신 고정 시각 Clock 을 넣어 자정 경계를 재현한다.
     */
    @Bean
    public Clock bookmarkClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
