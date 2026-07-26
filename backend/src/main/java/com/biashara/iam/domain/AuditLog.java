package com.biashara.iam.domain;

import com.biashara.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * An immutable record of a state-changing operation.
 *
 * {@code actorName} and {@code targetName} are denormalised copies rather than
 * joins: an audit trail has to stay readable after the user or row it references
 * has been renamed or removed.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_entity", columnList = "entityType")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    /** Snapshot of who acted, valid even if the account is later deleted. */
    @Column(nullable = false)
    private String actorName;

    private String actorRole;

    /** Verb phrase, e.g. "CREATE_USER", "RESET_PASSWORD", "VOID_SALE". */
    @Column(nullable = false)
    private String action;

    private String module;
    private String entityType;
    private Long entityId;

    /** Snapshot of what was acted upon, e.g. "Sarah Chebet". */
    private String targetName;

    @Column(length = 2000)
    private String details;

    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
