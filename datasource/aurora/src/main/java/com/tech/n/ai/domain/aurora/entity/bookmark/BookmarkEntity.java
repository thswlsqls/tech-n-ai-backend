package com.tech.n.ai.domain.aurora.entity.bookmark;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * BookmarkEntity - EmergingTech 전용
 */
@Entity
@Table(name = "bookmarks")
@Getter
@Setter
public class BookmarkEntity extends BaseEntity {

    // 태그 구분자
    private static final String TAG_DELIMITER = "|";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // EmergingTechDocument 비정규화 필드
    @Column(name = "emerging_tech_id", nullable = false, length = 24)
    private String emergingTechId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "published_at", precision = 6)
    private LocalDateTime publishedAt;

    @Column(name = "tag", length = 100)
    private String tag;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;
    
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
    
    public boolean canBeRestored(int daysLimit) {
        if (getDeletedAt() == null) {
            return false;
        }
        return getDeletedAt().isAfter(java.time.LocalDateTime.now().minusDays(daysLimit));
    }
    
    public void restore() {
        setIsDeleted(false);
        setDeletedAt(null);
        setDeletedBy(null);
    }
    
    // List<String> → String 변환 (저장용)
    public void setTagsAsList(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.tag = null;
        } else {
            this.tag = String.join(TAG_DELIMITER, tags);
        }
    }

    // String → List<String> 변환 (조회용)
    public List<String> getTagsAsList() {
        if (this.tag == null || this.tag.isBlank()) {
            return List.of();
        }
        return Arrays.asList(this.tag.split("\\" + TAG_DELIMITER));
    }

    // 요청 값이 null이면 기존 값 유지
    public void updateContent(List<String> tags, String memo) {
        if (tags != null) {
            setTagsAsList(tags);
        }
        if (memo != null) {
            this.memo = memo;
        }
    }
}
