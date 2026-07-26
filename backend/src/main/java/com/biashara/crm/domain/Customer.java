package com.biashara.crm.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.CustomerTier;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers", indexes = @Index(name = "idx_customer_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Customer extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    private String phone;
    private String email;
    private String address;
    private String city;

    /** INDIVIDUAL or BUSINESS — drives whether a tax PIN is expected. */
    private String customerType;

    private String taxPin;

    @Enumerated(EnumType.STRING)
    private CustomerTier tier;

    @Builder.Default
    @Column(nullable = false)
    private Integer loyaltyPoints = 0;

    @Column(precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(precision = 15, scale = 2)
    private BigDecimal outstandingBalance;

    // --- Analytics-maintained fields ---------------------------------------

    @Column(precision = 15, scale = 2)
    private BigDecimal totalSpent;

    private Integer totalOrders;

    @Column(precision = 15, scale = 2)
    private BigDecimal averageOrderValue;

    private LocalDateTime lastPurchaseAt;
    private LocalDate birthday;

    /** 0-100. Above 70 raises a CHURN_RISK insight with a retention action. */
    @Column(precision = 5, scale = 2)
    private BigDecimal churnRisk;

    /** Projected total spend over the modelled relationship. */
    @Column(precision = 15, scale = 2)
    private BigDecimal lifetimeValue;

    // --- RFM components, recomputed by the analytics module ------------------

    private Integer recencyScore;
    private Integer frequencyScore;
    private Integer monetaryScore;

    @Column(length = 1000)
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    public boolean isOverCreditLimit() {
        return creditLimit != null && outstandingBalance != null
                && outstandingBalance.compareTo(creditLimit) > 0;
    }

    /** Concatenated RFM digits, e.g. "543" — the standard segment label. */
    public String getRfmSegment() {
        if (recencyScore == null || frequencyScore == null || monetaryScore == null) {
            return null;
        }
        return "" + recencyScore + frequencyScore + monetaryScore;
    }
}
