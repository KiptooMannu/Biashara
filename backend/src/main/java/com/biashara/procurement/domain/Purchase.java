package com.biashara.procurement.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.PaymentStatus;
import com.biashara.common.enums.PurchaseStatus;
import com.biashara.iam.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A purchase order raised against a supplier. */
@Entity
@Table(name = "purchases", indexes = @Index(name = "idx_purchase_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Purchase extends TenantAwareEntity {

    @Column(nullable = false)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false)
    private LocalDate orderDate;

    private LocalDate expectedDelivery;
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal total;

    @Column(precision = 15, scale = 2)
    private BigDecimal amountPaid;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();

    public void addItem(PurchaseItem item) {
        item.setPurchase(this);
        items.add(item);
    }

    /** True when delivery is past due and the order has not been fully received. */
    public boolean isOverdue() {
        return expectedDelivery != null
                && receivedDate == null
                && status != PurchaseStatus.CANCELLED
                && expectedDelivery.isBefore(LocalDate.now());
    }
}
