package com.biashara.procurement.domain;

import com.biashara.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "suppliers", indexes = @Index(name = "idx_supplier_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Supplier extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    private String code;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String taxPin;

    /** Contractual lead time; compared against {@code averageDeliveryDays}. */
    private Integer leadTimeDays;

    /** Observed average, from purchase order receipts. */
    @Column(precision = 6, scale = 2)
    private BigDecimal averageDeliveryDays;

    /** 0-100, computed from on-time delivery rate and order accuracy. */
    @Column(precision = 5, scale = 2)
    private BigDecimal reliabilityScore;

    /** 1-5 stars, shown on the supplier scorecard. */
    private Integer rating;

    private Integer totalOrders;
    private Integer lateDeliveries;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalPurchaseValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal outstandingBalance;

    private String paymentTerms;

    @Column(length = 1000)
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Share of orders delivered on or before the expected date. */
    public BigDecimal getOnTimeRate() {
        if (totalOrders == null || totalOrders == 0) {
            return BigDecimal.ZERO;
        }
        int late = lateDeliveries == null ? 0 : lateDeliveries;
        return BigDecimal.valueOf(totalOrders - late)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalOrders), 1, java.math.RoundingMode.HALF_UP);
    }
}
