package com.tech.n.ai.api.bookmark.dto.response;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 북마크 상세 조회 응답 DTO
 */
public record BookmarkDetailResponse(
    String bookmarkTsid,
    String userId,
    String emergingTechId,
    String title,
    String url,
    String provider,
    String summary,
    LocalDateTime publishedAt,
    List<String> tags,
    String memo,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy
) {
    public static BookmarkDetailResponse from(BookmarkEntity entity) {
        if (entity == null) {
            return null;
        }

        return new BookmarkDetailResponse(
            entity.getId() != null ? entity.getId().toString() : null,
            entity.getUserId() != null ? entity.getUserId().toString() : null,
            entity.getEmergingTechId(),
            entity.getTitle(),
            entity.getUrl(),
            entity.getProvider(),
            entity.getSummary(),
            entity.getPublishedAt(),
            entity.getTagsAsList(),
            entity.getMemo(),
            entity.getCreatedAt(),
            entity.getCreatedBy() != null ? entity.getCreatedBy().toString() : null,
            entity.getUpdatedAt(),
            entity.getUpdatedBy() != null ? entity.getUpdatedBy().toString() : null
        );
    }
}
