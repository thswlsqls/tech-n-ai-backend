package com.tech.n.ai.batch.source.domain.emergingtech.scraper.processor;

import com.tech.n.ai.batch.source.domain.emergingtech.EmergingTechProcessorUtils;
import com.tech.n.ai.batch.source.domain.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.client.scraper.dto.ScrapedTechArticle;
import com.tech.n.ai.domain.mongodb.enums.EmergingTechType;
import com.tech.n.ai.domain.mongodb.enums.PostStatus;
import com.tech.n.ai.domain.mongodb.enums.SourceType;
import com.tech.n.ai.domain.mongodb.enums.TechProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * ScrapedTechArticle → EmergingTechCreateRequest 변환 Processor
 */
@Slf4j
@StepScope
public class EmergingTechScraperProcessor implements ItemProcessor<ScrapedTechArticle, EmergingTechCreateRequest> {

    @Override
    public @Nullable EmergingTechCreateRequest process(ScrapedTechArticle article) throws Exception {
        if (article.title() == null || article.url() == null) {
            log.debug("Skipping scraped article: title or url is null");
            return null;
        }

        return EmergingTechCreateRequest.builder()
            .provider(article.providerName())
            .updateType(classifyUpdateType(article).name())
            .title(article.title())
            .summary(article.summary())
            .url(article.url())
            .publishedAt(article.publishedDate())
            .sourceType(SourceType.WEB_SCRAPING.name())
            .status(PostStatus.PUBLISHED.name())
            .externalId("scraper:" + EmergingTechProcessorUtils.generateHash(article.url()))
            .metadata(EmergingTechCreateRequest.EmergingTechMetadataRequest.builder()
                .author(Objects.requireNonNullElse(article.author(), ""))
                .tags(EmergingTechProcessorUtils.extractTags(article.category()))
                .build())
            .build();
    }

    private EmergingTechType classifyUpdateType(ScrapedTechArticle article) {
        String title = article.title().toLowerCase();
        String category = article.category() != null ? article.category().toLowerCase() : "";

        if (title.contains("api") || category.contains("api")) return EmergingTechType.API_UPDATE;
        if (title.contains("model") || title.contains("release") || title.contains("introducing")
                || title.contains("announcing")) return EmergingTechType.MODEL_RELEASE;
        if (title.contains("launch") || title.contains("product") || title.contains("available")
                || title.contains("new service")) return EmergingTechType.PRODUCT_LAUNCH;
        if (title.contains("platform") || title.contains("cloud") || title.contains("infrastructure")
                || title.contains("update") || category.contains("platform")) return EmergingTechType.PLATFORM_UPDATE;
        return EmergingTechType.BLOG_POST;
    }
}
