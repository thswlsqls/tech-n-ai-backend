package com.tech.n.ai.domain.aurora.entity.auth;

import com.tech.n.ai.domain.aurora.annotation.Tsid;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AdminHistoryEntity
 */
@Entity
@Table(name = "admin_history")
@Getter
@Setter
public class AdminHistoryEntity {

    @Id
    @Tsid
    @Column(name = "history_id", nullable = false, updatable = false)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private AdminEntity admin;

    @Column(name = "admin_id", insertable = false, updatable = false, nullable = false)
    private Long adminId;

    @Column(name = "operation_type", length = 20, nullable = false)
    private String operationType;

    @Column(name = "before_data", columnDefinition = "JSON")
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "JSON")
    private String afterData;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false, precision = 6)
    private LocalDateTime changedAt;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }
}
