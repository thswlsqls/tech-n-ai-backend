package com.tech.n.ai.client.feign.domain.internal.contract;

import com.tech.n.ai.common.core.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Emerging Tech 내부 API Contract 인터페이스
 * Batch 모듈 및 AI Agent에서 api-emerging-tech 모듈의 내부 API 호출용
 */
public interface EmergingTechInternalContract {

    /**
     * Emerging Tech 단건 생성 (내부 API)
     */
    @PostMapping("/api/v1/emerging-tech/internal")
    ApiResponse<InternalApiDto.EmergingTechDetailResponse> createEmergingTechInternal(
        @RequestHeader("X-Internal-Api-Key") String apiKey,
        @RequestBody InternalApiDto.EmergingTechCreateRequest request);

    /**
     * Emerging Tech 다건 생성 (내부 API)
     */
    @PostMapping("/api/v1/emerging-tech/internal/batch")
    ApiResponse<InternalApiDto.EmergingTechBatchResponse> createEmergingTechBatchInternal(
        @RequestHeader("X-Internal-Api-Key") String apiKey,
        @RequestBody InternalApiDto.EmergingTechBatchRequest request);

    /**
     * Emerging Tech 검색
     */
    @GetMapping("/api/v1/emerging-tech/search")
    ApiResponse<Object> searchEmergingTech(
        @RequestHeader("X-Internal-Api-Key") String apiKey,
        @RequestParam("q") String query,
        @RequestParam(value = "provider", required = false) String provider,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size);

    /**
     * Emerging Tech 목록 조회 (필터 + 페이징)
     */
    @GetMapping("/api/v1/emerging-tech")
    ApiResponse<Object> listEmergingTechs(
        @RequestHeader("X-Internal-Api-Key") String apiKey,
        @RequestParam(value = "provider", required = false) String provider,
        @RequestParam(value = "updateType", required = false) String updateType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "startDate", required = false) String startDate,
        @RequestParam(value = "endDate", required = false) String endDate,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "sort", required = false) String sort);

    /**
     * Emerging Tech 상세 조회 (ID 기반)
     */
    @GetMapping("/api/v1/emerging-tech/{id}")
    ApiResponse<Object> getEmergingTechDetail(
        @RequestHeader("X-Internal-Api-Key") String apiKey,
        @PathVariable("id") String id);

    /**
     * Emerging Tech 승인 (내부 API)
     */
    @PostMapping("/api/v1/emerging-tech/{id}/approve")
    ApiResponse<Object> approveEmergingTech(
        @RequestHeader("X-Internal-Api-Key") String apiKey,
        @PathVariable("id") String id);
}
