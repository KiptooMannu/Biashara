package com.biashara.inventory.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.ProductType;
import com.biashara.procurement.domain.Supplier;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_tenant", columnList = "tenant_id"),
        @Index(name = "idx_product_sku", columnList = "sku")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Product extends TenantAwareEntity {

    @Column(nullable = false)
    private String sku;

    private String barcode;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Preferred supplier — the reorder suggestion engine defaults to this one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    /** Selling unit, e.g. "pc", "kg", "litre", "crate". */
    private String unit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal buyingPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(nullable = false)
    private Integer currentStock;

    @Column(nullable = false)
    private Integer minStock;

    private Integer maxStock;

    /** Trigger point for a reorder suggestion; usually above {@code minStock}. */
    private Integer reorderLevel;

    private LocalDate expiryDate;
    private String imageUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Units sold per day, recomputed by the analytics module. Drives stockout ETA. */
    @Column(precision = 10, scale = 2)
    private BigDecimal salesVelocity;

    // --- Derived ------------------------------------------------------------

    public BigDecimal getMargin() {
        if (sellingPrice == null || buyingPrice == null) {
            return BigDecimal.ZERO;
        }
        return sellingPrice.subtract(buyingPrice);
    }

    public BigDecimal getMarginPercent() {
        if (sellingPrice == null || sellingPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return getMargin().multiply(BigDecimal.valueOf(100))
                .divide(sellingPrice, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getStockValue() {
        return buyingPrice.multiply(BigDecimal.valueOf(currentStock == null ? 0 : currentStock));
    }

    public boolean isLowStock() {
        return currentStock != null && minStock != null && currentStock <= minStock;
    }

    public boolean isOutOfStock() {
        return currentStock != null && currentStock <= 0;
    }

    /**
     * Days until stock hits zero at the current sales velocity, or null when the
     * product has no measurable velocity.
     */
    public BigDecimal getDaysUntilStockout() {
        if (salesVelocity == null || salesVelocity.signum() <= 0 || currentStock == null) {
            return null;
        }
        return BigDecimal.valueOf(currentStock).divide(salesVelocity, 1, RoundingMode.HALF_UP);
    }
}
