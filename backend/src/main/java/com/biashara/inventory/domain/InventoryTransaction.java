package com.biashara.inventory.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.InventoryTransactionType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An append-only ledger of stock movement.
 *
 * {@code Product.currentStock} is a cached projection of this ledger; every change
 * to it is accompanied by a row here, which is what makes stock movement auditable
 * and lets the inventory-movement chart be built from real events.
 */
@Entity
@Table(name = "inventory_transactions", indexes = {
        @Index(name = "idx_invtxn_tenant", columnList = "tenant_id"),
        @Index(name = "idx_invtxn_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class InventoryTransaction extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryTransactionType type;

    /** Always positive; {@link #type} determines the direction. */
    @Column(nullable = false)
    private Integer quantity;

    /** Stock level after this movement was applied. */
    private Integer balanceAfter;

    @Column(precision = 15, scale = 2)
    private BigDecimal unitCost;

    /** Source document, e.g. "INV-000123" or "PO-00045". */
    private String reference;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User performedBy;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /** True when this movement increases stock on hand. */
    public boolean isInbound() {
        return type == InventoryTransactionType.STOCK_IN
                || type == InventoryTransactionType.RETURN
                || type == InventoryTransactionType.TRANSFER_IN;
    }
}
