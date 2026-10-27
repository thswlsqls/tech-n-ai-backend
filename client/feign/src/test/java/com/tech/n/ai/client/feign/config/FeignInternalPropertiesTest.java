package com.tech.n.ai.client.feign.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application-feign-internal.yml 의 내부 API 키 설정을 확인한다.
 * 실제 OS 환경 변수·시스템 프로퍼티는 넣지 않고, 테스트가 준 Map만 환경 변수로 쓴다.
 */
class FeignInternalPropertiesTest {

    private static final String YAML = "application-feign-internal.yml";
    private static final String EMERGING_TECH_KEY = "internal-api.emerging-tech.api-key";
    private static final String NEW_ENV = "EMERGING_TECH_INTERNAL_API_KEY";
    private static final String OLD_ENV = "INTERNAL_API_EMERGING_TECH_KEY";

    @Nested
    @DisplayName("internal-api.emerging-tech.api-key")
    class EmergingTechApiKey {

        @Test
        @DisplayName("서버와 같은 이름 EMERGING_TECH_INTERNAL_API_KEY만 있으면 그 값을 쓴다")
        void 새_이름만_있으면_그_값() throws IOException {
            // Given
            PropertySourcesPropertyResolver resolver = resolverWith(Map.of(NEW_ENV, "real-key"));

            // When
            String apiKey = resolver.getProperty(EMERGING_TECH_KEY);

            // Then
            assertThat(apiKey).isEqualTo("real-key");
        }

        @Test
        @DisplayName("두 환경 변수가 모두 없으면 기본값 default-emerging-tech-api-key를 쓴다")
        void 둘_다_없으면_기본값() throws IOException {
            // Given
            PropertySourcesPropertyResolver resolver = resolverWith(Map.of());

            // When
            String apiKey = resolver.getProperty(EMERGING_TECH_KEY);

            // Then
            assertThat(apiKey).isEqualTo("default-emerging-tech-api-key");
        }

        @Test
        @DisplayName("예전 이름 INTERNAL_API_EMERGING_TECH_KEY만 있으면 그 값을 쓴다")
        void 예전_이름만_있으면_그_값() throws IOException {
            // Given
            PropertySourcesPropertyResolver resolver = resolverWith(Map.of(OLD_ENV, "old-key"));

            // When
            String apiKey = resolver.getProperty(EMERGING_TECH_KEY);

            // Then
            assertThat(apiKey).isEqualTo("old-key");
        }

        @Test
        @DisplayName("두 이름이 모두 있으면 서버와 같은 이름 EMERGING_TECH_INTERNAL_API_KEY 값이 이긴다")
        void 둘_다_있으면_새_이름이_우선() throws IOException {
            // Given
            PropertySourcesPropertyResolver resolver =
                    resolverWith(Map.of(NEW_ENV, "real-key", OLD_ENV, "old-key"));

            // When
            String apiKey = resolver.getProperty(EMERGING_TECH_KEY);

            // Then
            assertThat(apiKey).isEqualTo("real-key");
        }
    }

    @Nested
    @DisplayName("다른 설정 값")
    class OtherProperties {

        @Test
        @DisplayName("Feign 클라이언트 URL과 contest·news 키는 그대로다")
        void url과_다른_키는_그대로() throws IOException {
            // Given
            PropertySourcesPropertyResolver resolver = resolverWith(Map.of(NEW_ENV, "real-key"));

            // When & Then
            assertThat(resolver.getProperty("feign.client.config.contest-internal-api.url"))
                    .isEqualTo("http://localhost:8081");
            assertThat(resolver.getProperty("feign.client.config.news-internal-api.url"))
                    .isEqualTo("http://localhost:8082");
            assertThat(resolver.getProperty("feign.client.config.emerging-tech-internal-api.url"))
                    .isEqualTo("http://localhost:8082");
            assertThat(resolver.getProperty("feign.client.config.agent-api.url"))
                    .isEqualTo("http://localhost:8086");
            assertThat(resolver.getProperty("internal-api.contest.api-key"))
                    .isEqualTo("default-contest-api-key");
            assertThat(resolver.getProperty("internal-api.news.api-key"))
                    .isEqualTo("default-news-api-key");
        }
    }

    private static PropertySourcesPropertyResolver resolverWith(Map<String, Object> env) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("testEnvironment", env));
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader().load(YAML, new ClassPathResource(YAML));
        yaml.forEach(sources::addLast);
        return new PropertySourcesPropertyResolver(sources);
    }
}
