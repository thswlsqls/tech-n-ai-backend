package com.tech.n.ai.api.emergingtech.controller;

import com.tech.n.ai.api.emergingtech.common.InternalApiKeyValidator;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechBatchRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechListRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechSearchRequest;
import com.tech.n.ai.api.emergingtech.dto.response.EmergingTechBatchResponse;
import com.tech.n.ai.api.emergingtech.dto.response.EmergingTechDetailResponse;
import com.tech.n.ai.api.emergingtech.dto.response.EmergingTechPageResponse;
import com.tech.n.ai.api.emergingtech.facade.EmergingTechFacade;
import com.tech.n.ai.common.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Emerging Tech API 컨트롤러
 */
@Validated
@RestController
@RequestMapping("/api/v1/emerging-tech")
@RequiredArgsConstructor
public class EmergingTechController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final EmergingTechFacade emergingTechFacade;
    private final InternalApiKeyValidator apiKeyValidator;

    /**
     * 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<EmergingTechPageResponse>> getEmergingTechList(
            @Valid EmergingTechListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(emergingTechFacade.getEmergingTechList(request)));
    }

    /**
     * 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmergingTechDetailResponse>> getEmergingTechDetail(
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(emergingTechFacade.getEmergingTechDetail(id)));
    }

    /**
     * 검색
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<EmergingTechPageResponse>> searchEmergingTech(
            @Valid EmergingTechSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(emergingTechFacade.searchEmergingTech(request)));
    }

    /**
     * 단건 생성 (내부 API)
     */
    @PostMapping("/internal")
    public ResponseEntity<ApiResponse<EmergingTechDetailResponse>> createEmergingTechInternal(
            @Valid @RequestBody EmergingTechCreateRequest request,
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey) {
        apiKeyValidator.validate(apiKey);
        return ResponseEntity.ok(ApiResponse.success(emergingTechFacade.createEmergingTech(request)));
    }

    /**
     * 다건 생성 (내부 API)
     */
    @PostMapping("/internal/batch")
    public ResponseEntity<ApiResponse<EmergingTechBatchResponse>> createEmergingTechBatchInternal(
            @Valid @RequestBody EmergingTechBatchRequest request,
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey) {
        apiKeyValidator.validate(apiKey);
        return ResponseEntity.ok(ApiResponse.success(emergingTechFacade.createEmergingTechBatch(request)));
    }

    /**
     * 승인 (내부 API)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<EmergingTechDetailResponse>> approveEmergingTech(
            @PathVariable String id,
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey) {
        apiKeyValidator.validate(apiKey);
        return ResponseEntity.ok(ApiResponse.success(emergingTechFacade.approveEmergingTech(id)));
    }

    /**
     * 거부 (내부 API)
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<EmergingTechDetailResponse>> rejectEmergingTech(
            @PathVariable String id,
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey) {
        apiKeyValidator.validate(apiKey);
        return ResponseEntity.ok(ApiResponse.success(emergingTechFacade.rejectEmergingTech(id)));
    }
}
