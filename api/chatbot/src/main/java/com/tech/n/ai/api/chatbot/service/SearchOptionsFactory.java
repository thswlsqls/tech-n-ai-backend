package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 검색 옵션 조립
 *
 * 운영 챗봇과 오프라인 평가 잡이 같은 옵션을 쓰도록 한 곳에 모아 둔다.
 */
@Component
public class SearchOptionsFactory {

    @Value("${chatbot.rag.max-search-results:5}")
    private int maxSearchResults;

    @Value("${chatbot.rag.min-similarity-score:0.7}")
    private double minSimilarityScore;

    @Value("${chatbot.rag.recency-months:6}")
    private int recencyMonths;

    public SearchOptions create(SearchQuery searchQuery) {
        boolean recency = searchQuery.context().isRecencyDetected();
        LocalDateTime dateFrom = recency ? LocalDateTime.now().minusMonths(recencyMonths) : null;

        return SearchOptions.builder()
            .includeEmergingTechs(searchQuery.context().includesEmergingTechs())
            .maxResults(maxSearchResults)
            .minSimilarityScore(minSimilarityScore)
            .providerFilters(searchQuery.context().getDetectedProviders())
            .updateTypeFilters(searchQuery.context().getDetectedUpdateTypes())
            .recencyDetected(recency)
            .dateFrom(dateFrom)
            .enableScoreFusion(true)
            .build();
    }
}
