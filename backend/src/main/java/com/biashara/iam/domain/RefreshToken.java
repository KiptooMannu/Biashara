package com.biashara.iam.domain;

import com.biashara.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * A server-side refresh token, so sessions can actually be revoked — the reason
 * refresh state is persisted rather than kept as a self-contained JWT.
 */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_token", columnList = "token", unique = true))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean revoked = false;

    private String ipAddress;
    private String device;

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(LocalDateTime.now());
    }
}
