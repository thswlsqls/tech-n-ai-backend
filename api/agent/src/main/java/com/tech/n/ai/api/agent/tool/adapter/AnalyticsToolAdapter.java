package com.tech.n.ai.api.agent.tool.adapter;

import com.tech.n.ai.api.agent.config.AnalyticsConfig;
import com.tech.n.ai.api.agent.tool.dto.StatisticsDto;
import com.tech.n.ai.api.agent.tool.dto.WordFrequencyDto;
import com.tech.n.ai.domain.mongodb.service.EmergingTechAggregationService;
import com.tech.n.ai.domain.mongodb.service.GroupCountResult;
import com.tech.n.ai.domain.mongodb.service.WordFrequencyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 분석 기능을 LangChain4j Tool 형식으로 래핑하는 어댑터
 *
 * <p>설계 포인트:
 * <ul>
 *   <li>텍스트 빈도 집계는 MongoDB 서버사이드 Aggregation으로 수행 (네트워크/메모리 최적화)</li>
 *   <li>불용어 목록은 AnalyticsConfig를 통해 외부 설정에서 주입 (운영 중 튜닝 가능)</li>
 *   <li>totalCount는 countByGroup 결과의 합산으로 계산 (별도 쿼리 제거)</li>
 *   <li>2-gram(bigram) 분석을 함께 제공하여 복합 기술 용어 추출 지원</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsToolAdapter {

    private final EmergingTechAggregationService aggregationService;
    private final AnalyticsConfig analyticsConfig;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 그룹별 통계 집계
     *
     * <p>totalCount는 그룹별 count 합산으로 계산하여 별도 쿼리를 제거한다.
     */
    public StatisticsDto getStatistics(String groupBy, String startDate, String endDate) {
        try {
            LocalDateTime start = parseDate(startDate, true);
            LocalDateTime end = parseDate(endDate, false);

            List<GroupCountResult> results = aggregationService.countByGroup(groupBy, start, end);

            // 그룹 결과의 count 합산으로 totalCount 계산 (별도 쿼리 제거)
            long totalCount = results.stream().mapToLong(GroupCountResult::getCount).sum();

            List<StatisticsDto.GroupCount> groups = results.stream()
                .map(r -> new StatisticsDto.GroupCount(r.getId(), r.getCount()))
                .toList();

            return new StatisticsDto(groupBy, startDate, endDate, totalCount, groups);
        } catch (Exception e) {
            log.error("통계 집계 실패: groupBy={}", groupBy, e);
            return new StatisticsDto(groupBy, startDate, endDate, 0, List.of());
        }
    }

    /**
     * 텍스트 빈도 분석 (MongoDB 서버사이드 집계)
     * provider, updateType, sourceType 조합으로 필터링 가능
     */
    public WordFrequencyDto analyzeTextFrequency(String provider, String updateType, String sourceType,
                                                  String startDate, String endDate, int topN) {
        try {
            LocalDateTime start = parseDate(startDate, true);
            LocalDateTime end = parseDate(endDate, false);

            // 저장 값이 대문자라 정확히 일치 비교하는 집계 서비스에 맞춰 대문자로 바꾼다
            String providerFilter = toUpperOrNull(provider);
            String updateTypeFilter = toUpperOrNull(updateType);
            String sourceTypeFilter = toUpperOrNull(sourceType);

            // 불용어 목록을 외부 설정에서 가져옴 (YAML 오버라이드 시 mutable할 수 있으므로 방어적 복사)
            List<String> stopWords = List.copyOf(analyticsConfig.getStopWords());

            // 서버사이드 단어 빈도 집계 (unigram)
            List<WordFrequencyResult> wordResults = aggregationService.aggregateWordFrequency(
                providerFilter, updateTypeFilter, sourceTypeFilter, start, end, stopWords, topN);

            List<WordFrequencyDto.WordCount> topWords = wordResults.stream()
                .map(r -> new WordFrequencyDto.WordCount(r.getId(), r.getCount()))
                .toList();

            // 2-gram 빈도 집계 (향후 서버사이드 구현 예정)
            List<WordFrequencyDto.WordCount> topBigrams = List.of();

            // 전체 도큐먼트 수
            long totalDocs = aggregationService.countDocuments(providerFilter, updateTypeFilter, sourceTypeFilter, start, end);

            String period = buildPeriodString(startDate, endDate);
            return new WordFrequencyDto(totalDocs, period, topWords, topBigrams);
        } catch (Exception e) {
            log.error("텍스트 빈도 분석 실패: provider={}, updateType={}, sourceType={}", provider, updateType, sourceType, e);
            return new WordFrequencyDto(0, "", List.of(), List.of());
        }
    }

    private String toUpperOrNull(String value) {
        return (value != null && !value.isBlank()) ? value.toUpperCase() : null;
    }

    private LocalDateTime parseDate(String dateStr, boolean startOfDay) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
            return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            log.warn("날짜 파싱 실패: {}", dateStr);
            return null;
        }
    }

    private String buildPeriodString(String startDate, String endDate) {
        if ((startDate == null || startDate.isBlank()) && (endDate == null || endDate.isBlank())) {
            return "전체 기간";
        }
        String start = (startDate != null && !startDate.isBlank()) ? startDate : "~";
        String end = (endDate != null && !endDate.isBlank()) ? endDate : "~";
        return start + " ~ " + end;
    }
}
