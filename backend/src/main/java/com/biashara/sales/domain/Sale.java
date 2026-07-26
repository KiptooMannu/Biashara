package com.biashara.sales.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.PaymentMethod;
import com.biashara.common.enums.PaymentStatus;
import com.biashara.common.enums.SaleStatus;
import com.biashara.common.enums.SalesChannel;
import com.biashara.crm.domain.Customer;
import com.biashara.iam.domain.Branch;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A completed sale.
 *
 * Cost of goods is captured per line at the moment of sale, so gross profit stays
 * correct forever even after supplier prices move.
 */
@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sale_tenant", columnList = "tenant_id"),
        @Index(name = "idx_sale_date", columnList = "saleDate")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Sale extends TenantAwareEntity {

    @Column(nullable = false)
    private String invoiceNumber;

    /** Null for a walk-in cash sale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    private LocalDateTime saleDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /** Sum of line-level cost snapshots — the basis for gross profit. */
    @Column(precision = 15, scale = 2)
    private BigDecimal costOfGoods;

    @Column(precision = 15, scale = 2)
    private BigDecimal amountPaid;

    @Column(precision = 15, scale = 2)
    private BigDecimal changeGiven;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus status;

    @Enumerated(EnumType.STRING)
    private SalesChannel channel;

    /** M-Pesa transaction code or card authorisation reference. */
    private String paymentReference;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    public BigDecimal getGrossProfit() {
        BigDecimal cost = costOfGoods == null ? BigDecimal.ZERO : costOfGoods;
        BigDecimal net = subtotal == null ? BigDecimal.ZERO : subtotal;
        return net.subtract(cost);
    }
}
