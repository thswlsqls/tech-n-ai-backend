package com.tech.n.ai.api.agent.tool.dto;

import java.util.List;

/**
 * 통계 집계 결과 DTO
 * LangChain4j가 JSON 직렬화하여 LLM에게 전달
 */
public record StatisticsDto(
    String groupBy,
    String startDate,
    String endDate,
    long totalCount,
    List<GroupCount> groups,
    String message
) {
    /**
     * 메시지 없는 기본 생성자 (정상 응답용)
     */
    public StatisticsDto(String groupBy, String startDate, String endDate,
                         long totalCount, List<GroupCount> groups) {
        this(groupBy, startDate, endDate, totalCount, groups, null);
    }

    public record GroupCount(
        String name,
        long count
    ) {}
}
