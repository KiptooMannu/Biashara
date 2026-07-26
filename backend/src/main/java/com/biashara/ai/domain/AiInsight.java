package com.biashara.ai.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.InsightType;
import com.biashara.common.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A generated business insight.
 *
 * The shape encodes BIASHARA's core premise — never state a number without
 * explaining it. {@code title} is what happened, {@code cause} is why, and
 * {@code recommendation} is what to do about it. All three are required for an
 * insight to be worth showing.
 */
@Entity
@Table(name = "ai_insights", indexes = @Index(name = "idx_insight_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AiInsight extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsightType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    /** What happened, e.g. "Sales decreased 18% this week". */
    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String summary;

    /** Why it happened, e.g. "Cooking oil stocked out on Tuesday". */
    @Column(length = 1000)
    private String cause;

    /** What to do next, e.g. "Raise the reorder level by 20%". */
    @Column(length = 1000)
    private String recommendation;

    /** Headline figure, e.g. "Revenue at risk". */
    private String metricLabel;

    @Column(precision = 15, scale = 2)
    private BigDecimal metricValue;

    private String metricUnit;

    /** Percentage change behind the insight; negative for a decline. */
    @Column(precision = 8, scale = 2)
    private BigDecimal changePercent;

    /** Model confidence, 0-100. Shown so the owner can weigh the advice. */
    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    /** Owning module, for filtering: Sales, Inventory, Finance, CRM, HR. */
    private String module;

    /** Deep link into the screen that lets the user act on this insight. */
    private String actionUrl;

    private String actionLabel;

    /** Entity this insight is about, so the UI can link to it. */
    private String entityType;
    private Long entityId;
    private String entityName;

    @Builder.Default
    @Column(nullable = false)
    private boolean read = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean dismissed = false;

    @Column(nullable = false)
    private LocalDateTime generatedAt;
}
