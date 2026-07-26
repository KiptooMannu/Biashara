package com.biashara.sales.domain;

import com.biashara.common.domain.BaseEntity;
import com.biashara.inventory.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SaleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Snapshot: a receipt must reprint identically years later. */
    @Column(nullable = false)
    private String productName;

    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Buying price at the time of sale — never re-read from the product. */
    @Column(precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(precision = 15, scale = 2)
    private BigDecimal discount;

    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    private Integer returnedQuantity;

    public BigDecimal getLineProfit() {
        BigDecimal cost = unitCost == null ? BigDecimal.ZERO : unitCost;
        return unitPrice.subtract(cost).multiply(BigDecimal.valueOf(quantity));
    }
}
