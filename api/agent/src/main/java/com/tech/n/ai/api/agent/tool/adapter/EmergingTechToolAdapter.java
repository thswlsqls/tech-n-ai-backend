package com.tech.n.ai.api.agent.tool.adapter;

import com.tech.n.ai.api.agent.tool.dto.EmergingTechDetailDto;
import com.tech.n.ai.api.agent.tool.dto.EmergingTechDto;
import com.tech.n.ai.api.agent.tool.dto.EmergingTechListDto;
import com.tech.n.ai.api.agent.tool.dto.InternalApiResponse;
import com.tech.n.ai.client.feign.domain.internal.contract.EmergingTechInternalContract;
import com.tech.n.ai.common.core.dto.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emerging Tech API를 LangChain4j Tool 형식으로 래핑하는 어댑터
 * EmergingTechInternalContract를 통해 api-emerging-tech 모듈 호출
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmergingTechToolAdapter {

    private final EmergingTechInternalContract emergingTechContract;
    private final ObjectMapper objectMapper;

    @Value("${internal-api.emerging-tech.api-key:}")
    private String apiKey;

    private static final String SUCCESS_CODE = "2000";

    /**
     * Emerging Tech 검색
     *
     * @param query 검색 키워드
     * @param provider 기술 제공자 필터 (빈 문자열이면 전체 검색)
     * @return 검색 결과 목록
     */
    public List<EmergingTechDto> search(String query, String provider) {
        try {
            String providerParam = toNullIfBlank(provider);
            ApiResponse<Object> response = emergingTechContract.searchEmergingTech(apiKey, query, providerParam, 0, 20);

            InternalApiResponse.PageResponse page = extractPageResponse(response);
            if (page == null || page.items() == null) {
                return List.of();
            }

            return page.items().stream().map(this::toEmergingTechDto).toList();
        } catch (Exception e) {
            log.error("Emerging Tech 검색 실패: query={}, provider={}", query, provider, e);
            return List.of();
        }
    }

    /**
     * 목록 조회 (필터 + 페이징)
     */
    public EmergingTechListDto list(String startDate, String endDate,
                                     String provider, String updateType,
                                     String sourceType, String status,
                                     int page, int size) {
        try {
            ApiResponse<Object> response = emergingTechContract.listEmergingTechs(
                apiKey, toNullIfBlank(provider), toNullIfBlank(updateType),
                toNullIfBlank(status), toNullIfBlank(sourceType),
                toNullIfBlank(startDate), toNullIfBlank(endDate),
                page, size, "publishedAt,desc"
            );

            InternalApiResponse.PageResponse pageResponse = extractPageResponse(response);
            if (pageResponse == null) {
                return EmergingTechListDto.empty(page, size, buildPeriodString(startDate, endDate));
            }

            int totalCount = pageResponse.totalCount();
            int pageSize = pageResponse.pageSize();
            int totalPages = (totalCount + pageSize - 1) / pageSize;

            List<EmergingTechDto> items = (pageResponse.items() != null)
                ? pageResponse.items().stream().map(this::toEmergingTechDto).toList()
                : List.of();

            return new EmergingTechListDto(
                totalCount, pageResponse.pageNumber(), pageSize,
                totalPages, buildPeriodString(startDate, endDate), items
            );
        } catch (Exception e) {
            log.error("Emerging Tech 목록 조회 실패", e);
            return EmergingTechListDto.empty(page, size, buildPeriodString(startDate, endDate));
        }
    }

    /**
     * 상세 조회 (ID 기반)
     */
    public EmergingTechDetailDto getDetail(String id) {
        try {
            ApiResponse<Object> response = emergingTechContract.getEmergingTechDetail(apiKey, id);

            if (!SUCCESS_CODE.equals(response.code()) || response.data() == null) {
                log.warn("Emerging Tech 상세 조회 실패: code={}, message={}", response.code(), response.message());
                return EmergingTechDetailDto.notFound(id);
            }

            InternalApiResponse.DetailResponse detail =
                objectMapper.convertValue(response.data(), InternalApiResponse.DetailResponse.class);

            return toEmergingTechDetailDto(detail);
        } catch (Exception e) {
            log.error("Emerging Tech 상세 조회 실패: id={}", id, e);
            return EmergingTechDetailDto.notFound(id);
        }
    }

    // ========== 내부 변환 메서드 ==========

    /**
     * ApiResponse에서 PageResponse 추출
     */
    private InternalApiResponse.PageResponse extractPageResponse(ApiResponse<Object> response) {
        if (!SUCCESS_CODE.equals(response.code()) || response.data() == null) {
            log.warn("Emerging Tech API 응답 실패: code={}, message={}", response.code(), response.message());
            return null;
        }
        return objectMapper.convertValue(response.data(), InternalApiResponse.PageResponse.class);
    }

    /**
     * Internal API DetailResponse → LangChain4j Tool용 EmergingTechDto 변환
     */
    private EmergingTechDto toEmergingTechDto(InternalApiResponse.DetailResponse detail) {
        return new EmergingTechDto(
            detail.id(),
            detail.provider(),
            detail.updateType(),
            detail.title(),
            detail.url(),
            detail.status()
        );
    }

    /**
     * Internal API DetailResponse → LangChain4j Tool용 EmergingTechDetailDto 변환
     */
    private EmergingTechDetailDto toEmergingTechDetailDto(InternalApiResponse.DetailResponse detail) {
        EmergingTechDetailDto.EmergingTechMetadataDto metadata = null;
        if (detail.metadata() != null) {
            InternalApiResponse.MetadataResponse m = detail.metadata();
            metadata = new EmergingTechDetailDto.EmergingTechMetadataDto(
                m.version(),
                m.tags() != null ? m.tags() : List.of(),
                m.author(),
                m.githubRepo()
            );
        }

        return new EmergingTechDetailDto(
            detail.id(),
            detail.provider(),
            detail.updateType(),
            detail.title(),
            detail.summary(),
            detail.url(),
            detail.publishedAt(),
            detail.sourceType(),
            detail.status(),
            detail.externalId(),
            detail.createdAt(),
            detail.updatedAt(),
            metadata
        );
    }

    private String toNullIfBlank(String value) {
        return (value != null && !value.isBlank()) ? value : null;
    }

    private String buildPeriodString(String startDate, String endDate) {
        if ((startDate == null || startDate.isBlank()) && (endDate == null || endDate.isBlank())) {
            return "전체";
        }
        String start = (startDate != null && !startDate.isBlank()) ? startDate : "~";
        String end = (endDate != null && !endDate.isBlank()) ? endDate : "~";
        return start + " ~ " + end;
    }
}
