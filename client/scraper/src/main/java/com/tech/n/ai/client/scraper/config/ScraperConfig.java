package com.tech.n.ai.client.scraper.config;


import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;


@Configuration
@EnableConfigurationProperties(ScraperProperties.class)
public class ScraperConfig {

    @Bean("scraperWebClientBuilder")
    public WebClient.Builder scraperWebClientBuilder(ScraperProperties properties) {
        int timeoutSeconds = properties.getTimeoutSeconds();
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)); // 10MB
    }
}
