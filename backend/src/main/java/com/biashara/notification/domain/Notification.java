package com.biashara.notification.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.NotificationChannel;
import com.biashara.common.enums.Severity;
import com.biashara.iam.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notification_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Notification extends TenantAwareEntity {

    /** Null means the notification is addressed to the whole business. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User recipient;

    /** Only users holding this permission see the notification. Null means everyone. */
    private String requiredPermission;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    /** Originating module: Inventory, Sales, Finance, HR, CRM, Procurement. */
    private String module;

    private String actionUrl;
    private String icon;

    @Builder.Default
    @Column(nullable = false)
    private boolean read = false;

    /**
     * Whether the message was handed to its channel. Always true for IN_APP; for
     * EMAIL/SMS/WHATSAPP it stays false in the demo, since no external gateway is
     * wired up — the row records the intent to send.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean dispatched = false;

    @Column(nullable = false)
    private LocalDateTime createdOn;
}
