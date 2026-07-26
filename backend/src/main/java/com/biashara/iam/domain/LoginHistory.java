package com.biashara.iam.domain;

import com.biashara.common.domain.BaseEntity;
import com.biashara.common.enums.LoginStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Every authentication attempt, successful or not.
 *
 * Not tenant-scoped via {@code TenantAwareEntity} on purpose: a failed login may
 * arrive for an email that matches no user, so there is no tenant to attribute it
 * to. The tenant is recorded when it is known.
 */
@Entity
@Table(name = "login_history", indexes = @Index(name = "idx_login_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LoginHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /** Recorded even when no user matches, so credential stuffing is visible. */
    @Column(nullable = false)
    private String attemptedEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginStatus status;

    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    private String device;
    private String location;
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
