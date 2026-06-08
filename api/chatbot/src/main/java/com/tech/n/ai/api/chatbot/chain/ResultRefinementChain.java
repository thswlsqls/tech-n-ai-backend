package com.tech.n.ai.api.chatbot.chain;

import com.tech.n.ai.api.chatbot.service.ReRankingService;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultRefinementChain {

    private final ReRankingService reRankingService;

    @Value("${chatbot.rag.max-search-results:5}")
    private int maxSearchResults;

    /**
     * 검색 결과 정제 (Re-Ranking + Recency Boost 적용)
     *
     * @param query 원본 쿼리
     * @param rawResults 원본 검색 결과
     * @return 정제된 검색 결과
     */
    public List<SearchResult> refine(String query, List<SearchResult> rawResults) {
        return refine(query, rawResults, false);
    }

    /**
     * 검색 결과 정제 (Re-Ranking + Recency Boost 적용)
     *
     * @param query 원본 쿼리
     * @param rawResults 원본 검색 결과
     * @param recencyDetected 최신성 키워드 감지 여부
     * @return 정제된 검색 결과
     */
    public List<SearchResult> refine(String query, List<SearchResult> rawResults, boolean recencyDetected) {
        return refine(query, rawResults, recencyDetected, false);
    }

    /**
     * 검색 결과 정제 (Re-Ranking + Recency Boost 적용)
     *
     * @param query 원본 쿼리
     * @param rawResults 원본 검색 결과
     * @param recencyDetected 최신성 키워드 감지 여부
     * @param scoreFusionApplied Score Fusion이 이미 적용된 경우 true (Java-level Recency Boost 생략)
     * @return 정제된 검색 결과
     */
    public List<SearchResult> refine(String query, List<SearchResult> rawResults,
                                      boolean recencyDetected, boolean scoreFusionApplied) {
        // 1. 중복 제거
        List<SearchResult> deduplicated = removeDuplicates(rawResults);

        // 2. Re-Ranking 적용 (활성화된 경우)
        if (reRankingService.isEnabled()) {
            log.info("Applying Re-Ranking for query: {}", query);
            List<SearchResult> reranked = reRankingService.rerank(query, deduplicated, maxSearchResults * 2);
            if (scoreFusionApplied) {
                log.info("Score Fusion already applied, skipping Java-level Recency Boost");
                return sortByScoreDescAndLimit(reranked);
            }
            return applyRecencyBoost(reranked, recencyDetected);
        }

        // 3. Score Fusion 이미 적용된 경우 Recency Boost 생략
        if (scoreFusionApplied) {
            log.info("Score Fusion already applied, skipping Java-level Recency Boost");
            return sortByScoreDescAndLimit(deduplicated);
        }

        // 4. Recency Boost 적용 후 정렬
        return applyRecencyBoost(deduplicated, recencyDetected);
    }

    /**
     * Recency Boost 적용
     *
     * - 일반 쿼리: hybridScore = similarity * 0.85 + recencyScore * 0.15
     * - 최신성 쿼리: hybridScore = similarity * 0.5 + recencyScore * 0.5
     * - recencyScore = 1.0 / (1.0 + daysSincePublished / 365.0)
     */
    private List<SearchResult> applyRecencyBoost(List<SearchResult> results, boolean recencyDetected) {
        double similarityWeight = recencyDetected ? 0.5 : 0.85;
        double recencyWeight = recencyDetected ? 0.5 : 0.15;

        log.info("Applying recency boost: recencyDetected={}, similarityWeight={}, recencyWeight={}",
            recencyDetected, similarityWeight, recencyWeight);

        List<SearchResult> boosted = results.stream()
            .map(result -> {
                double recencyScore = calculateRecencyScore(result);
                double hybridScore = (result.score() * similarityWeight) + (recencyScore * recencyWeight);

                if (log.isDebugEnabled()) {
                    log.debug("Recency boost: doc={}, similarity={}, recencyScore={}, hybridScore={}",
                        getTitle(result), result.score(), recencyScore, hybridScore);
                }

                return SearchResult.builder()
                    .documentId(result.documentId())
                    .text(result.text())
                    .score(hybridScore)
                    .collectionType(result.collectionType())
                    .metadata(result.metadata())
                    .build();
            })
            .collect(Collectors.toList());

        return sortByScoreDescAndLimit(boosted);
    }

    /**
     * score 내림차순 정렬 후 maxSearchResults 개수로 제한
     */
    private List<SearchResult> sortByScoreDescAndLimit(List<SearchResult> results) {
        return results.stream()
            .sorted(Comparator.comparing(SearchResult::score).reversed())
            .limit(maxSearchResults)
            .collect(Collectors.toList());
    }

    /**
     * 문서의 recency score 계산 (0~1, 최신일수록 1에 가까움)
     */
    private double calculateRecencyScore(SearchResult result) {
        LocalDateTime publishedAt = getPublishedAt(result);
        if (publishedAt == null) {
            return 0.5; // published_at이 없으면 중간값
        }

        long daysSince = ChronoUnit.DAYS.between(publishedAt, LocalDateTime.now());
        if (daysSince < 0) daysSince = 0;

        return 1.0 / (1.0 + daysSince / 365.0);
    }

    /**
     * SearchResult 메타데이터에서 published_at 추출
     */
    private LocalDateTime getPublishedAt(SearchResult result) {
        if (result.metadata() instanceof Document doc) {
            Object publishedAt = doc.get("published_at");
            if (publishedAt instanceof LocalDateTime ldt) {
                return ldt;
            }
            if (publishedAt instanceof java.util.Date date) {
                return date.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
            }
        }
        return null;
    }

    /**
     * SearchResult 메타데이터에서 title 추출 (로깅용)
     */
    private String getTitle(SearchResult result) {
        if (result.metadata() instanceof Document doc) {
            return doc.getString("title");
        }
        return result.documentId();
    }

    /**
     * 중복 제거
     */
    private List<SearchResult> removeDuplicates(List<SearchResult> results) {
        Set<String> seenIds = new HashSet<>();
        return results.stream()
            .filter(r -> seenIds.add(r.documentId()))
            .collect(Collectors.toList());
    }
}
