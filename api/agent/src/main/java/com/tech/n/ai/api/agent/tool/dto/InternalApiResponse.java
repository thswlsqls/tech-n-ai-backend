package com.tech.n.ai.api.agent.tool.dto;

import java.util.List;
import java.util.Map;

/**
 * EmergingTech Internal API 응답 매핑 DTO
 *
 * <p>api-emerging-tech 모듈의 응답 구조를 타입 안전하게 역직렬화하기 위한 DTO 모음.
 * Feign 계약이 {@code ApiResponse<Object>}를 반환하므로, ObjectMapper.convertValue()로 변환한다.
 */
public final class InternalApiResponse {

    private InternalApiResponse() {
    }

    /**
     * 목록/검색 응답 (페이징 포함)
     */
    public record PageResponse(
        int pageSize,
        int pageNumber,
        int totalCount,
        List<DetailResponse> items
    ) {}

    /**
     * 상세 조회 응답
     */
    public record DetailResponse(
        String id,
        String provider,
        String updateType,
        String title,
        String summary,
        String url,
        String publishedAt,
        String sourceType,
        String status,
        String externalId,
        MetadataResponse metadata,
        String createdAt,
        String updatedAt
    ) {}

    /**
     * 메타데이터 응답
     */
    public record MetadataResponse(
        String version,
        List<String> tags,
        String author,
        String githubRepo,
        Map<String, Object> additionalInfo
    ) {}
}
