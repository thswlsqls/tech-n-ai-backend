package com.tech.n.ai.api.agent.tool;

import com.tech.n.ai.api.agent.exception.AgentLoopDetectedException;
import com.tech.n.ai.api.agent.metrics.ToolExecutionMetrics;
import com.tech.n.ai.api.agent.tool.adapter.AnalyticsToolAdapter;
import com.tech.n.ai.api.agent.tool.adapter.DataCollectionToolAdapter;
import com.tech.n.ai.api.agent.tool.adapter.EmergingTechToolAdapter;
import com.tech.n.ai.api.agent.tool.adapter.SlackToolAdapter;
import com.tech.n.ai.api.agent.tool.dto.DataCollectionResultDto;
import com.tech.n.ai.api.agent.tool.dto.EmergingTechDetailDto;
import com.tech.n.ai.api.agent.tool.dto.EmergingTechDto;
import com.tech.n.ai.api.agent.tool.dto.EmergingTechListDto;
import com.tech.n.ai.api.agent.tool.dto.StatisticsDto;
import com.tech.n.ai.api.agent.tool.dto.ToolResult;
import com.tech.n.ai.api.agent.tool.dto.WordFrequencyDto;
import com.tech.n.ai.api.agent.tool.validation.ToolInputValidator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emerging Tech Agent용 LangChain4j Tool 모음
 *
 * <p>모든 Tool 메서드는 다음 패턴을 따름:
 * <ol>
 *   <li>입력값 검증 (ToolInputValidator 사용)</li>
 *   <li>검증 실패 시 빈 결과 또는 ToolResult.failure 반환</li>
 *   <li>검증 성공 시 Adapter를 통해 외부 API 호출</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmergingTechAgentTools {

    private final SlackToolAdapter slackAdapter;
    private final EmergingTechToolAdapter emergingTechAdapter;
    private final AnalyticsToolAdapter analyticsAdapter;
    private final DataCollectionToolAdapter dataCollectionAdapter;

    /** 실행별 메트릭 (동시 실행 격리를 위해 ThreadLocal 사용) */
    private final ThreadLocal<ToolExecutionMetrics> currentMetrics = new ThreadLocal<>();

    /**
     * 실행 전 메트릭 바인딩
     * @param metrics 이번 실행에서 사용할 메트릭 인스턴스
     */
    public void bindMetrics(ToolExecutionMetrics metrics) {
        currentMetrics.set(metrics);
    }

    /**
     * 실행 후 메트릭 해제 (메모리 누수 방지)
     */
    public void unbindMetrics() {
        currentMetrics.remove();
    }

    private ToolExecutionMetrics metrics() {
        ToolExecutionMetrics m = currentMetrics.get();
        if (m == null) {
            throw new IllegalStateException("ToolExecutionMetrics가 바인딩되지 않았습니다. bindMetrics()를 먼저 호출하세요.");
        }
        return m;
    }

    /**
     * 검증 결과를 확인하고, 실패 시 메트릭 증가 및 경고 로그를 남긴다.
     *
     * @param error 검증 에러 메시지 (null이면 검증 성공)
     * @return 검증 실패 여부 (true: 실패, false: 성공)
     */
    private boolean hasValidationError(String error) {
        if (error == null) {
            return false;
        }
        metrics().incrementValidationError();
        log.warn("Tool 입력값 검증 실패: {}", error);
        return true;
    }

    /**
     * 동일 Tool + 동일 인자의 연속 중복 호출을 감지한다.
     * LLM이 Tool 결과를 무시하고 같은 호출을 반복하는 루프를 방어한다.
     *
     * @param toolName Tool 이름
     * @param args     Tool 호출 인자를 직렬화한 문자열
     * @return 허용 횟수를 초과한 연속 중복이면 {@code true}
     */
    /** 연속 중복 호출로 STOP 메시지를 반환한 뒤에도 루프가 계속되면 예외를 던지는 임계값 */
    private static final int LOOP_FORCE_STOP_THRESHOLD = 5;

    private boolean isConsecutiveDuplicate(String toolName, String args) {
        if (metrics().isConsecutiveDuplicate(toolName, args)) {
            int count = metrics().getConsecutiveDuplicateCount();
            log.warn("연속 중복 호출 감지: tool={}, args={}, count={}", toolName, args, count);

            // STOP 메시지를 여러 번 반환해도 루프가 계속되면 강제 종료
            if (count > LOOP_FORCE_STOP_THRESHOLD) {
                throw new AgentLoopDetectedException(
                        "%s 연속 중복 호출 %d회 초과. 동일한 인자로 반복 호출하는 루프가 감지되어 강제 종료합니다."
                                .formatted(toolName, count));
            }
            return true;
        }
        return false;
    }

    /**
     * 저장된 Emerging Tech 업데이트 검색
     */
    @Tool(name = "search_emerging_techs",
          value = "저장된 Emerging Tech 업데이트를 검색합니다. 중복 확인이나 기존 데이터 조회에 사용합니다.")
    public List<EmergingTechDto> searchEmergingTechs(
            @P("검색 키워드") String query,
            @P("기술 제공자 필터 (OPENAI, ANTHROPIC, GOOGLE, META, XAI 또는 빈 문자열)") String provider
    ) {
        metrics().incrementToolCall();
        log.info("Tool 호출: search_emerging_techs(query={}, provider={})", query, provider);

        if (hasValidationError(ToolInputValidator.validateRequired(query, "query"))) {
            return List.of();
        }
        if (hasValidationError(ToolInputValidator.validateProviderOptional(provider))) {
            return List.of();
        }

        return emergingTechAdapter.search(query, provider);
    }

    /**
     * 기간/필터별 Emerging Tech 목록 조회
     */
    @Tool(name = "list_emerging_techs",
          value = "MongoDB Atlas emerging_techs 컬렉션에서 조건에 맞는 도큐먼트 목록을 조회합니다. "
                + "published_at 기준 기간 필터, provider, update_type, source_type, status 필터를 조합할 수 있습니다.")
    public EmergingTechListDto listEmergingTechs(
        @P("조회 시작일 (YYYY-MM-DD 형식, 빈 문자열이면 제한 없음)") String startDate,
        @P("조회 종료일 (YYYY-MM-DD 형식, 빈 문자열이면 제한 없음)") String endDate,
        @P("Provider 필터 (OPENAI, ANTHROPIC, GOOGLE, META, XAI 또는 빈 문자열이면 전체)") String provider,
        @P("UpdateType 필터 (MODEL_RELEASE, API_UPDATE, SDK_RELEASE, PRODUCT_LAUNCH, PLATFORM_UPDATE, BLOG_POST 또는 빈 문자열이면 전체)") String updateType,
        @P("SourceType 필터 (GITHUB_RELEASE, RSS, WEB_SCRAPING 또는 빈 문자열이면 전체)") String sourceType,
        @P("Status 필터 (DRAFT, PENDING, PUBLISHED, REJECTED 또는 빈 문자열이면 전체)") String status,
        @P("페이지 번호 (1부터 시작, 기본값 1)") int page,
        @P("페이지 크기 (기본값 20, 최대 100)") int size
    ) {
        metrics().incrementToolCall();
        log.info("Tool 호출: list_emerging_techs(startDate={}, endDate={}, provider={}, updateType={}, sourceType={}, status={}, page={}, size={})",
                startDate, endDate, provider, updateType, sourceType, status, page, size);

        // 입력값 검증
        EmergingTechListDto emptyResult = EmergingTechListDto.empty(page, size, "전체");
        if (hasValidationError(ToolInputValidator.validateDateOptional(startDate, "startDate"))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateDateOptional(endDate, "endDate"))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateProviderOptional(provider))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateUpdateTypeOptional(updateType))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateSourceTypeOptional(sourceType))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateStatusOptional(status))) {
            return emptyResult;
        }

        // 페이지 정규화
        int normalizedPage = ToolInputValidator.normalizePage(page);
        int normalizedSize = ToolInputValidator.normalizeSize(size);

        return emergingTechAdapter.list(startDate, endDate, provider, updateType,
                                         sourceType, status, normalizedPage, normalizedSize);
    }

    /**
     * Emerging Tech 상세 조회 (ID 기반)
     */
    @Tool(name = "get_emerging_tech_detail",
          value = "Emerging Tech 도큐먼트의 상세 정보를 ID로 조회합니다. "
                + "목록 조회나 검색 결과에서 얻은 ID를 사용합니다.")
    public EmergingTechDetailDto getEmergingTechDetail(
        @P("조회할 도큐먼트 ID (MongoDB ObjectId)") String id
    ) {
        metrics().incrementToolCall();
        log.info("Tool 호출: get_emerging_tech_detail(id={})", id);

        if (hasValidationError(ToolInputValidator.validateObjectId(id))) {
            return EmergingTechDetailDto.notFound(id);
        }

        return emergingTechAdapter.getDetail(id);
    }

    /**
     * Provider/SourceType/UpdateType별 통계 집계
     */
    @Tool(name = "get_emerging_tech_statistics",
          value = "조회 기간 기준으로 EmergingTech 데이터를 Provider, SourceType, UpdateType별로 집계합니다. "
                + "결과를 Markdown 표와 Mermaid 차트로 정리하여 보여줄 수 있습니다.")
    public StatisticsDto getStatistics(
        @P("집계 기준 필드: provider, source_type, update_type") String groupBy,
        @P("조회 시작일 (YYYY-MM-DD 형식, 빈 문자열이면 전체 기간)") String startDate,
        @P("조회 종료일 (YYYY-MM-DD 형식, 빈 문자열이면 전체 기간)") String endDate
    ) {
        metrics().incrementToolCall();
        metrics().incrementAnalyticsCall();
        log.info("Tool 호출: get_emerging_tech_statistics(groupBy={}, startDate={}, endDate={})",
                groupBy, startDate, endDate);

        // 연속 중복 호출 감지 - LLM이 같은 통계를 반복 요청하는 루프 방어
        String callArgs = "groupBy=%s,startDate=%s,endDate=%s".formatted(groupBy, startDate, endDate);
        if (isConsecutiveDuplicate("get_emerging_tech_statistics", callArgs)) {
            return new StatisticsDto(groupBy, startDate, endDate, 0, List.of(),
                    "STOP: 이 통계는 이미 조회되었습니다. 이전에 받은 결과를 사용하여 Markdown 표와 Mermaid 차트로 응답을 작성하세요. 이 Tool을 다시 호출하지 마세요.");
        }

        // 입력 검증
        StatisticsDto emptyResult = new StatisticsDto(groupBy, startDate, endDate, 0, List.of());
        if (hasValidationError(ToolInputValidator.validateGroupByField(groupBy))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateDateOptional(startDate, "startDate"))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateDateOptional(endDate, "endDate"))) {
            return emptyResult;
        }

        // groupBy를 MongoDB 필드명으로 정규화
        String resolvedGroupBy = ToolInputValidator.resolveGroupByField(groupBy);
        return analyticsAdapter.getStatistics(resolvedGroupBy, startDate, endDate);
    }

    /**
     * title/summary 텍스트 키워드 빈도 분석
     */
    @Tool(name = "analyze_text_frequency",
          value = "EmergingTech 도큐먼트의 title, summary에서 주요 키워드 빈도를 분석합니다. "
                + "Provider, UpdateType, SourceType으로 필터링할 수 있습니다. "
                + "Mermaid 차트나 Word Cloud 형태로 결과를 정리할 수 있습니다.")
    public WordFrequencyDto analyzeTextFrequency(
        @P("Provider 필터 (OPENAI, ANTHROPIC, GOOGLE, META, XAI 또는 빈 문자열이면 전체)") String provider,
        @P("UpdateType 필터 (MODEL_RELEASE, API_UPDATE, SDK_RELEASE, PRODUCT_LAUNCH, PLATFORM_UPDATE, BLOG_POST 또는 빈 문자열이면 전체)") String updateType,
        @P("SourceType 필터 (GITHUB_RELEASE, RSS, WEB_SCRAPING 또는 빈 문자열이면 전체)") String sourceType,
        @P("조회 시작일 (YYYY-MM-DD 형식, 빈 문자열이면 전체 기간)") String startDate,
        @P("조회 종료일 (YYYY-MM-DD 형식, 빈 문자열이면 전체 기간)") String endDate,
        @P("상위 키워드 개수 (기본값 20)") int topN
    ) {
        metrics().incrementToolCall();
        metrics().incrementAnalyticsCall();
        log.info("Tool 호출: analyze_text_frequency(provider={}, updateType={}, sourceType={}, startDate={}, endDate={}, topN={})",
                provider, updateType, sourceType, startDate, endDate, topN);

        // 연속 중복 호출 감지
        String callArgs = "provider=%s,updateType=%s,sourceType=%s,startDate=%s,endDate=%s,topN=%d"
                .formatted(provider, updateType, sourceType, startDate, endDate, topN);
        if (isConsecutiveDuplicate("analyze_text_frequency", callArgs)) {
            return new WordFrequencyDto(0, "",  List.of(), List.of(),
                    "STOP: 이 키워드 분석은 이미 수행되었습니다. 이전에 받은 결과를 사용하여 응답을 작성하세요. 이 Tool을 다시 호출하지 마세요.");
        }

        WordFrequencyDto emptyResult = new WordFrequencyDto(0, "", List.of(), List.of());
        if (hasValidationError(ToolInputValidator.validateProviderOptional(provider))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateUpdateTypeOptional(updateType))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateSourceTypeOptional(sourceType))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateDateOptional(startDate, "startDate"))) {
            return emptyResult;
        }
        if (hasValidationError(ToolInputValidator.validateDateOptional(endDate, "endDate"))) {
            return emptyResult;
        }

        int effectiveTopN = (topN > 0 && topN <= 100) ? topN : 20;
        return analyticsAdapter.analyzeTextFrequency(provider, updateType, sourceType, startDate, endDate, effectiveTopN);
    }

    /**
     * Slack 채널에 메시지 전송
     */
    @Tool(name = "send_slack_notification",
          value = "Slack 채널에 메시지를 전송합니다. 관리자 알림에 사용합니다.")
    public ToolResult sendSlackNotification(
            @P("메시지 내용") String message
    ) {
        metrics().incrementToolCall();
        log.info("Tool 호출: send_slack_notification(messageLength={})", message != null ? message.length() : 0);

        if (hasValidationError(ToolInputValidator.validateRequired(message, "message"))) {
            return ToolResult.failure("message는 필수 입력값입니다.");
        }

        return slackAdapter.sendNotification(message);
    }

    // ========== 데이터 수집 Tool ==========

    /**
     * GitHub 저장소 릴리스를 수집하여 DB에 저장
     */
    @Tool(name = "collect_github_releases",
          value = "GitHub 저장소의 릴리스를 수집하여 DB에 저장합니다. "
                + "수집 결과(신규/중복/실패 건수)를 반환합니다.")
    public DataCollectionResultDto collectGitHubReleases(
            @P("저장소 소유자 (예: openai, anthropics, google, meta-llama)") String owner,
            @P("저장소 이름 (예: openai-python, anthropic-sdk-python)") String repo
    ) {
        metrics().incrementToolCall();

        String correctedOwner = ToolInputValidator.correctGitHubOwner(owner);
        String correctedRepo = ToolInputValidator.correctGitHubRepo(correctedOwner, repo);
        if (!correctedOwner.equals(owner) || !correctedRepo.equals(repo)) {
            log.info("Tool 호출: collect_github_releases(owner={} → {}, repo={} → {})", owner, correctedOwner, repo, correctedRepo);
        } else {
            log.info("Tool 호출: collect_github_releases(owner={}, repo={})", owner, repo);
        }

        if (hasValidationError(ToolInputValidator.validateGitHubRepo(correctedOwner, correctedRepo))) {
            return DataCollectionResultDto.failure("GITHUB_RELEASES", "", 0, "GitHub 저장소 입력값이 유효하지 않습니다.");
        }

        DataCollectionResultDto result = dataCollectionAdapter.collectGitHubReleases(correctedOwner, correctedRepo);

        // 수집 완료된 저장소 기록
        metrics().markGitHubRepoCollected(correctedOwner, correctedRepo);

        return result;
    }

    /**
     * OpenAI/Google AI 블로그 RSS 피드를 수집하여 DB에 저장
     */
    @Tool(name = "collect_rss_feeds",
          value = "OpenAI/Google AI 블로그 RSS 피드를 수집하여 DB에 저장합니다. "
                + "수집 결과(신규/중복/실패 건수)를 반환합니다. "
                + "이미 수집한 provider를 다시 호출하지 마세요. 수집 완료 후 결과를 요약하고 작업을 종료하세요.")
    public DataCollectionResultDto collectRssFeeds(
            @P("제공자 필터 (OPENAI, GOOGLE 또는 빈 문자열=전체 수집)") String provider
    ) {
        metrics().incrementToolCall();
        log.info("Tool 호출: collect_rss_feeds(provider={})", provider);

        if (hasValidationError(ToolInputValidator.validateRssProviderOptional(provider))) {
            return DataCollectionResultDto.failure("RSS_FEEDS", provider, 0, "RSS provider 값이 유효하지 않습니다.");
        }

        // 이미 수집 완료된 provider면 중복 수집 차단
        if (metrics().isRssProviderCollected(provider)) {
            int blockedCount = metrics().incrementAndGetCollectBlockedCount();
            log.warn("이미 collect_rss_feeds로 수집된 provider 재수집 차단: {} (차단 횟수: {})",
                    provider, blockedCount);

            if (blockedCount > 3) {
                throw new AgentLoopDetectedException(
                        "collect_rss_feeds 반복 차단 %d회 초과. 이미 수집 완료된 RSS provider를 계속 재수집하는 루프가 감지되었습니다."
                                .formatted(blockedCount));
            }

            return DataCollectionResultDto.failure("RSS_FEEDS", provider, 0,
                    "BLOCKED: provider '%s' RSS 피드는 이미 수집 완료되었습니다. 이 provider를 다시 수집하지 마세요. 수집 결과를 요약하고 작업을 완료하세요."
                            .formatted(provider == null || provider.isBlank() ? "전체" : provider));
        }

        DataCollectionResultDto result = dataCollectionAdapter.collectRssFeeds(provider);

        // 수집 완료된 provider 기록
        metrics().markRssProviderCollected(provider);

        return result;
    }

    /**
     * Anthropic/Meta AI 기술 블로그를 크롤링하여 DB에 저장
     */
    @Tool(name = "collect_scraped_articles",
          value = "Anthropic/Meta AI 기술 블로그를 크롤링하여 DB에 저장합니다. "
                + "수집 결과(신규/중복/실패 건수)를 반환합니다. "
                + "이미 수집한 provider를 다시 호출하지 마세요. 수집 완료 후 결과를 요약하고 작업을 종료하세요.")
    public DataCollectionResultDto collectScrapedArticles(
            @P("제공자 필터 (ANTHROPIC, META 또는 빈 문자열=전체 수집)") String provider
    ) {
        metrics().incrementToolCall();
        log.info("Tool 호출: collect_scraped_articles(provider={})", provider);

        if (hasValidationError(ToolInputValidator.validateScraperProviderOptional(provider))) {
            return DataCollectionResultDto.failure("WEB_SCRAPING", provider, 0, "Scraper provider 값이 유효하지 않습니다.");
        }

        // 이미 수집 완료된 provider면 중복 수집 차단
        if (metrics().isScraperProviderCollected(provider)) {
            int blockedCount = metrics().incrementAndGetCollectBlockedCount();
            log.warn("이미 collect_scraped_articles로 수집된 provider 재수집 차단: {} (차단 횟수: {})",
                    provider, blockedCount);

            if (blockedCount > 3) {
                throw new AgentLoopDetectedException(
                        "collect_scraped_articles 반복 차단 %d회 초과. 이미 수집 완료된 provider를 계속 재수집하는 루프가 감지되었습니다."
                                .formatted(blockedCount));
            }

            return DataCollectionResultDto.failure("WEB_SCRAPING", provider, 0,
                    "BLOCKED: provider '%s' 블로그는 이미 수집 완료되었습니다. 이 provider를 다시 수집하지 마세요. 수집 결과를 요약하고 작업을 완료하세요."
                            .formatted(provider == null || provider.isBlank() ? "전체" : provider));
        }

        DataCollectionResultDto result = dataCollectionAdapter.collectScrapedArticles(provider);

        // 수집 완료된 provider 기록
        metrics().markScraperProviderCollected(provider);

        return result;
    }
}
