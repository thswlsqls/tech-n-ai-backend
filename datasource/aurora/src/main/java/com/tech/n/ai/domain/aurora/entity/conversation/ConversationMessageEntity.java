package com.tech.n.ai.domain.aurora.entity.conversation;

import com.tech.n.ai.domain.aurora.annotation.Tsid;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 대화 메시지 엔티티
 */
@Entity
@Table(name = "conversation_messages", schema = "chatbot")
@Getter
@Setter
public class ConversationMessageEntity {

    @Id
    @Tsid
    @Column(name = "message_id", nullable = false, updatable = false)
    private Long messageId;  // TSID Primary Key

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ConversationSessionEntity session;

    @Column(name = "role", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageRole role;  // USER, ASSISTANT, SYSTEM

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;  // 선택: 토큰 수 (비용 계산용)

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;  // 대화 순서 (1부터 시작)

    @Column(name = "created_at", nullable = false, precision = 6)
    private LocalDateTime createdAt;

    public enum MessageRole {
        USER, ASSISTANT, SYSTEM
    }
}
