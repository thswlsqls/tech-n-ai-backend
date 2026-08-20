package com.tech.n.ai.domain.aurora.entity.bookmark;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * BookmarkDailyStatEntity - 사용자·날짜·제공자별 조회 집계
 *
 * 리포트 API 가 읽는 쪽이다. 조회 이벤트가 들어올 때마다 갱신한다.
 */
@Entity
@Table(
    name = "bookmark_daily_stats",
    indexes = {
        @Index(name = "idx_bookmark_daily_stats_user_date", columnList = "user_id, stat_date")
    }
)
@Getter
@Setter
public class BookmarkDailyStatEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    public static BookmarkDailyStatEntity of(Long userId, LocalDate statDate, String provider) {
        BookmarkDailyStatEntity entity = new BookmarkDailyStatEntity();
        entity.userId = userId;
        entity.statDate = statDate;
        entity.provider = provider;
        entity.viewCount = 0L;
        return entity;
    }

    public void increaseViewCount() {
        this.viewCount = this.viewCount + 1;
    }
}
