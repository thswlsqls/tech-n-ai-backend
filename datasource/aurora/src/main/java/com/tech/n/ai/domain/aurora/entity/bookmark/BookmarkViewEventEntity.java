package com.tech.n.ai.domain.aurora.entity.bookmark;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * BookmarkViewEventEntity - 북마크 조회 이벤트 원본
 *
 * 사용자가 북마크 상세를 열 때마다 한 건씩 쌓인다.
 * 집계 결과는 {@link BookmarkDailyStatEntity} 에 따로 둔다.
 */
@Entity
@Table(
    name = "bookmark_view_events",
    indexes = {
        @Index(name = "idx_bookmark_view_events_user_viewed", columnList = "user_id, viewed_at")
    }
)
@Getter
@Setter
public class BookmarkViewEventEntity extends BaseEntity {

    @Column(name = "bookmark_id", nullable = false)
    private Long bookmarkId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "viewed_at", nullable = false, precision = 6)
    private LocalDateTime viewedAt;

    /** 조회 경로. 웹/앱 구분에 쓴다. */
    @Column(name = "source", length = 20)
    private String source;

    public static BookmarkViewEventEntity of(Long bookmarkId, Long userId, String provider,
                                             LocalDateTime viewedAt, String source) {
        BookmarkViewEventEntity entity = new BookmarkViewEventEntity();
        entity.bookmarkId = bookmarkId;
        entity.userId = userId;
        entity.provider = provider;
        entity.viewedAt = viewedAt;
        entity.source = source;
        return entity;
    }
}
