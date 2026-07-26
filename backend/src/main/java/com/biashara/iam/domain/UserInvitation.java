package com.biashara.iam.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * The record of an account invitation.
 *
 * In production this is consumed by the notification service and emailed. There is
 * no SMTP server in the demo, so the rendered invitation — including the one-time
 * temporary password — is persisted here and surfaced in the admin UI instead.
 * {@code temporaryPassword} is deliberately readable for exactly that reason and
 * is cleared the moment the user completes their first login.
 */
@Entity
@Table(name = "user_invitations")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserInvitation extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    /** Demo-visibility only; nulled on first successful password change. */
    private String temporaryPassword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime acceptedAt;

    /** Rendered invitation body, so the demo can show exactly what would be sent. */
    @Column(length = 2000)
    private String emailBody;

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
