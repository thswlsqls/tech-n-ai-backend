package com.tech.n.ai.domain.aurora.entity.conversation;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 대화 세션 엔티티
 */
@Entity
@Table(name = "conversation_sessions", schema = "chatbot")
@Getter
@Setter
@AttributeOverride(name = "id", column = @Column(name = "session_id"))
public class ConversationSessionEntity extends BaseEntity {

    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    @Column(name = "title", length = 200)
    private String title;  // 선택: 세션 제목

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;  // 활성 세션 여부

    // BaseEntity에서 상속: id (TSID), isDeleted, deletedAt, deletedBy, createdAt, createdBy, updatedAt, updatedBy
}
