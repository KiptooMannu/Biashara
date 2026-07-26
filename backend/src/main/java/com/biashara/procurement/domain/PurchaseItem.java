package com.biashara.procurement.domain;

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
@Table(name = "purchase_items")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PurchaseItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Snapshot: the PO must still read correctly if the product is renamed. */
    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    /** Short-delivery is normal in procurement, so this is tracked separately. */
    private Integer receivedQuantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(precision = 15, scale = 2)
    private BigDecimal lineTotal;

    public Integer getOutstandingQuantity() {
        return quantity - (receivedQuantity == null ? 0 : receivedQuantity);
    }
}
