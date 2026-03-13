package com.tech.n.ai.api.bookmark.dto.response;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 북마크 히스토리 상세 조회 응답 DTO
 */
public record BookmarkHistoryDetailResponse(
    String historyId,
    String entityId,
    String operationType,
    Map<String, Object> beforeData,
    Map<String, Object> afterData,
    String changedBy,
    LocalDateTime changedAt,
    String changeReason
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * BookmarkHistoryEntity로부터 BookmarkHistoryDetailResponse 생성
     * 
     * @param entity BookmarkHistoryEntity
     * @return BookmarkHistoryDetailResponse
     */
    @SuppressWarnings("unchecked")
    public static BookmarkHistoryDetailResponse from(BookmarkHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        
        Map<String, Object> beforeDataMap = null;
        Map<String, Object> afterDataMap = null;
        
        try {
            if (entity.getBeforeData() != null) {
                beforeDataMap = objectMapper.readValue(
                    entity.getBeforeData(),
                    Map.class
                );
            }
            if (entity.getAfterData() != null) {
                afterDataMap = objectMapper.readValue(
                    entity.getAfterData(),
                    Map.class
                );
            }
        } catch (Exception e) {
            // JSON 파싱 실패 시 null로 설정
        }
        
        return new BookmarkHistoryDetailResponse(
            entity.getHistoryId() != null ? entity.getHistoryId().toString() : null,
            entity.getBookmarkId() != null ? entity.getBookmarkId().toString() : null,
            entity.getOperationType(),
            beforeDataMap,
            afterDataMap,
            entity.getChangedBy() != null ? entity.getChangedBy().toString() : null,
            entity.getChangedAt(),
            entity.getChangeReason()
        );
    }
}
