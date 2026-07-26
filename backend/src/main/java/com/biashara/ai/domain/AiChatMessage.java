package com.biashara.ai.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.iam.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/** One turn in an AI assistant conversation. */
@Entity
@Table(name = "ai_chat_messages", indexes = @Index(name = "idx_chat_conversation", columnList = "conversationId"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AiChatMessage extends TenantAwareEntity {

    /** Groups messages into a thread. */
    @Column(nullable = false)
    private String conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** USER or ASSISTANT. */
    @Column(nullable = false)
    private String role;

    @Lob
    @Column(nullable = false, length = 8000)
    private String content;

    /**
     * Which analytics query answered this question. Recorded so an answer can be
     * traced back to the figures it was derived from rather than taken on trust.
     */
    private String dataSource;

    @Column(nullable = false)
    private LocalDateTime sentAt;
}
