package com.tech.n.ai.api.emergingtech.facade;

import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechBatchRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechListRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechSearchRequest;
import com.tech.n.ai.api.emergingtech.dto.response.EmergingTechBatchResponse;
import com.tech.n.ai.api.emergingtech.dto.response.EmergingTechDetailResponse;
import com.tech.n.ai.api.emergingtech.dto.response.EmergingTechPageResponse;
import com.tech.n.ai.api.emergingtech.service.EmergingTechCommandService;
import com.tech.n.ai.api.emergingtech.service.EmergingTechQueryService;
import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.dto.PageData;
import com.tech.n.ai.common.core.exception.BusinessException;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.enums.EmergingTechType;
import com.tech.n.ai.domain.mongodb.enums.PostStatus;
import com.tech.n.ai.domain.mongodb.enums.SourceType;
import com.tech.n.ai.domain.mongodb.enums.TechProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Emerging Tech Facade
 * Controller와 Service 사이의 오케스트레이션 계층
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergingTechFacade {

    private static final String DEFAULT_SORT_FIELD = "publishedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "publishedAt", "createdAt", "updatedAt", "title", "provider"
    );

    private final EmergingTechQueryService queryService;
    private final EmergingTechCommandService commandService;

    /**
     * 목록 조회
     */
    public EmergingTechPageResponse getEmergingTechList(EmergingTechListRequest request) {
        validateFilters(request.provider(), request.updateType(), request.status(), request.sourceType());

        Pageable pageable = PageRequest.of(
            request.page() - 1,
            request.size(),
            parseSort(request.sort())
        );

        Page<EmergingTechDocument> page = queryService.findEmergingTechs(
            request.provider(),
            request.updateType(),
            request.status(),
            request.sourceType(),
            request.startDate(),
            request.endDate(),
            pageable
        );

        return toPageResponse(page, request.page(), request.size());
    }

    /**
     * 상세 조회
     */
    public EmergingTechDetailResponse getEmergingTechDetail(String id) {
        EmergingTechDocument document = queryService.findEmergingTechById(id);
        return EmergingTechDetailResponse.from(document);
    }

    /**
     * 검색
     */
    public EmergingTechPageResponse searchEmergingTech(EmergingTechSearchRequest request) {
        Pageable pageable = PageRequest.of(
            request.page() - 1,
            request.size()
        );

        Page<EmergingTechDocument> page = queryService.searchEmergingTech(
            request.q(),
            pageable
        );

        return toPageResponse(page, request.page(), request.size());
    }

    /**
     * 단건 생성
     */
    public EmergingTechDetailResponse createEmergingTech(EmergingTechCreateRequest request) {
        EmergingTechCommandService.SaveResult result = commandService.saveEmergingTech(request);
        EmergingTechDetailResponse response = EmergingTechDetailResponse.from(result.document());
        log.info("Emerging Tech 생성 완료: id={}, title={}, provider={}, isNew={}",
            response.id(), response.title(), response.provider(), result.isNew());
        return response;
    }

    /**
     * 다건 생성.
     *
     * 중복 검사·임베딩·저장을 요청 단위로 한 번씩 처리한다.
     * 저장이 실패하면 그 요청을 전건 실패로 보고한다 — 항목 단위 집계가 아니다.
     */
    public EmergingTechBatchResponse createEmergingTechBatch(EmergingTechBatchRequest request) {
        int newCount = 0;
        int duplicateCount = 0;
        int failureCount = 0;
        List<String> failureMessages = new ArrayList<>();

        try {
            for (EmergingTechCommandService.SaveResult result : commandService.saveEmergingTechAll(request.items())) {
                if (result.isNew()) {
                    newCount++;
                } else {
                    duplicateCount++;
                }
            }
        } catch (Exception e) {
            failureCount = request.items().size();
            String errorMessage = String.format(
                "Emerging Tech 저장 실패: count=%d, error=%s",
                request.items().size(), e.getMessage()
            );
            log.error(errorMessage, e);
            failureMessages.add(errorMessage);
        }

        log.info("Emerging Tech 다건 생성 완료: total={}, new={}, duplicate={}, failure={}",
            request.items().size(), newCount, duplicateCount, failureCount);

        return EmergingTechBatchResponse.builder()
            .totalCount(request.items().size())
            .successCount(newCount + duplicateCount)
            .newCount(newCount)
            .duplicateCount(duplicateCount)
            .failureCount(failureCount)
            .failureMessages(failureMessages)
            .build();
    }

    /**
     * 승인
     */
    public EmergingTechDetailResponse approveEmergingTech(String id) {
        EmergingTechDocument document = commandService.updateStatus(id, PostStatus.PUBLISHED);
        return EmergingTechDetailResponse.from(document);
    }

    /**
     * 거부
     */
    public EmergingTechDetailResponse rejectEmergingTech(String id) {
        EmergingTechDocument document = commandService.updateStatus(id, PostStatus.REJECTED);
        return EmergingTechDetailResponse.from(document);
    }

    /**
     * Page → PageResponse 변환 (공통 헬퍼)
     */
    private EmergingTechPageResponse toPageResponse(Page<EmergingTechDocument> page, int pageNumber, int pageSize) {
        List<EmergingTechDetailResponse> list = page.getContent().stream()
            .map(EmergingTechDetailResponse::from)
            .toList();

        PageData<EmergingTechDetailResponse> pageData = PageData.of(
            pageSize,
            pageNumber,
            (int) page.getTotalElements(),
            list
        );

        return EmergingTechPageResponse.from(pageData);
    }

    /**
     * 필터 값 유효성 검증
     */
    private void validateFilters(String provider, String updateType, String status, String sourceType) {
        if (provider != null) {
            validateEnumValue(TechProvider.class, provider, "provider");
        }
        if (updateType != null) {
            validateEnumValue(EmergingTechType.class, updateType, "updateType");
        }
        if (status != null) {
            validateEnumValue(PostStatus.class, status, "status");
        }
        if (sourceType != null) {
            validateEnumValue(SourceType.class, sourceType, "sourceType");
        }
    }

    /**
     * enum 값 유효성 검증
     */
    private <E extends Enum<E>> void validateEnumValue(Class<E> enumClass, String value, String fieldName) {
        try {
            Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.toString(enumClass.getEnumConstants());
            throw new BusinessException(
                ErrorCodeConstants.BAD_REQUEST,
                ErrorCodeConstants.MESSAGE_CODE_BAD_REQUEST,
                String.format("유효하지 않은 %s 값입니다: '%s'. 허용된 값: %s", fieldName, value, validValues)
            );
        }
    }

    /**
     * 정렬 문자열 파싱 및 검증
     */
    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD);
        }

        String[] parts = sort.split(",");
        if (parts.length != 2) {
            throw new BusinessException(
                ErrorCodeConstants.BAD_REQUEST,
                ErrorCodeConstants.MESSAGE_CODE_BAD_REQUEST,
                "정렬 형식이 올바르지 않습니다. 올바른 형식: 'field,direction' (예: publishedAt,desc)"
            );
        }

        String field = parts[0].trim();
        String direction = parts[1].trim().toLowerCase();

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new BusinessException(
                ErrorCodeConstants.BAD_REQUEST,
                ErrorCodeConstants.MESSAGE_CODE_BAD_REQUEST,
                String.format("유효하지 않은 정렬 필드입니다: '%s'. 허용된 필드: %s", field, ALLOWED_SORT_FIELDS)
            );
        }

        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new BusinessException(
                ErrorCodeConstants.BAD_REQUEST,
                ErrorCodeConstants.MESSAGE_CODE_BAD_REQUEST,
                String.format("유효하지 않은 정렬 방향입니다: '%s'. 허용된 값: [asc, desc]", direction)
            );
        }

        Sort.Direction sortDirection = "asc".equals(direction)
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        return Sort.by(sortDirection, field);
    }
}
