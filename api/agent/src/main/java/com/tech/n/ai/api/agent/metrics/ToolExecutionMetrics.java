package com.tech.n.ai.api.agent.metrics;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tool 실행 메트릭 추적
 * 매 Agent 실행마다 새 인스턴스를 생성하여 스레드 안전성 보장
 */
public class ToolExecutionMetrics {

    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private final AtomicInteger analyticsCallCount = new AtomicInteger(0);
    private final AtomicInteger validationErrorCount = new AtomicInteger(0);

    /** 연속 중복 호출 감지용 */
    private volatile String lastToolCallKey;
    private final AtomicInteger consecutiveDuplicateCount = new AtomicInteger(0);

    /** 연속 중복 호출 허용 최대 횟수 (이 값 초과 시 차단) */
    private static final int MAX_CONSECUTIVE_DUPLICATES = 2;

    /** collect_github_releases로 이미 수집 완료된 저장소 (owner/repo 형식) */
    private final Set<String> collectedGitHubRepos = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** collect_rss_feeds로 이미 수집 완료된 provider */
    private final Set<String> collectedRssProviders = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** collect_scraped_articles로 이미 수집 완료된 provider */
    private final Set<String> collectedScraperProviders = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** collect_rss_feeds/collect_scraped_articles 중복 차단 횟수 (루프 감지용) */
    private final AtomicInteger collectBlockedCount = new AtomicInteger(0);


    public void incrementToolCall() {
        toolCallCount.incrementAndGet();
    }

    public void incrementAnalyticsCall() {
        analyticsCallCount.incrementAndGet();
    }

    public void incrementValidationError() {
        validationErrorCount.incrementAndGet();
    }

    /**
     * GitHub 저장소를 수집 완료로 기록
     * @param owner 저장소 소유자
     * @param repo 저장소 이름
     */
    public void markGitHubRepoCollected(String owner, String repo) {
        collectedGitHubRepos.add(owner.toLowerCase() + "/" + repo.toLowerCase());
    }

    /**
     * 해당 GitHub 저장소가 이미 수집 완료되었는지 확인
     * @param owner 저장소 소유자
     * @param repo 저장소 이름
     * @return 이미 수집된 경우 true
     */
    public boolean isGitHubRepoCollected(String owner, String repo) {
        return collectedGitHubRepos.contains(owner.toLowerCase() + "/" + repo.toLowerCase());
    }

    public int getToolCallCount() {
        return toolCallCount.get();
    }

    public int getAnalyticsCallCount() {
        return analyticsCallCount.get();
    }

    public int getValidationErrorCount() {
        return validationErrorCount.get();
    }

    // ========== RSS 수집 추적 ==========

    /**
     * RSS provider를 수집 완료로 기록
     * @param provider provider 이름 (OPENAI, GOOGLE, 또는 빈 문자열=전체)
     */
    public void markRssProviderCollected(String provider) {
        String key = (provider == null || provider.isBlank()) ? "_ALL_" : provider.toUpperCase();
        collectedRssProviders.add(key);
    }

    /**
     * 해당 RSS provider가 이미 수집 완료되었는지 확인
     * @param provider provider 이름
     * @return 이미 수집된 경우 true
     */
    public boolean isRssProviderCollected(String provider) {
        String key = (provider == null || provider.isBlank()) ? "_ALL_" : provider.toUpperCase();
        // 전체 수집이 완료되었으면 개별 provider도 수집된 것으로 간주
        return collectedRssProviders.contains(key) || collectedRssProviders.contains("_ALL_");
    }

    // ========== Scraper 수집 추적 ==========

    /**
     * Scraper provider를 수집 완료로 기록
     * @param provider provider 이름 (ANTHROPIC, META, 또는 빈 문자열=전체)
     */
    public void markScraperProviderCollected(String provider) {
        String key = (provider == null || provider.isBlank()) ? "_ALL_" : provider.toUpperCase();
        collectedScraperProviders.add(key);
    }

    /**
     * 해당 Scraper provider가 이미 수집 완료되었는지 확인
     * @param provider provider 이름
     * @return 이미 수집된 경우 true
     */
    public boolean isScraperProviderCollected(String provider) {
        String key = (provider == null || provider.isBlank()) ? "_ALL_" : provider.toUpperCase();
        return collectedScraperProviders.contains(key) || collectedScraperProviders.contains("_ALL_");
    }

    // ========== 수집 루프 감지 ==========

    /**
     * collect 중복 차단 횟수를 증가시키고 현재 값을 반환
     * @return 증가 후의 차단 횟수
     */
    public int incrementAndGetCollectBlockedCount() {
        return collectBlockedCount.incrementAndGet();
    }

    public int getCollectBlockedCount() {
        return collectBlockedCount.get();
    }

    // ========== 연속 중복 호출 감지 ==========

    /**
     * 동일 Tool + 동일 인자의 연속 호출 여부를 판단한다.
     * 호출할 때마다 내부 상태를 갱신하므로, Tool 메서드 진입 직후 한 번만 호출해야 한다.
     *
     * @param toolName Tool 이름
     * @param args     Tool 호출 인자를 직렬화한 문자열
     * @return 허용 횟수를 초과한 연속 중복 호출이면 {@code true}
     */
    public boolean isConsecutiveDuplicate(String toolName, String args) {
        String currentKey = toolName + "::" + Objects.toString(args, "");
        if (currentKey.equals(lastToolCallKey)) {
            return consecutiveDuplicateCount.incrementAndGet() > MAX_CONSECUTIVE_DUPLICATES;
        }
        // 새로운 호출 패턴이면 카운터 리셋
        lastToolCallKey = currentKey;
        consecutiveDuplicateCount.set(1);
        return false;
    }

    public int getConsecutiveDuplicateCount() {
        return consecutiveDuplicateCount.get();
    }
}
