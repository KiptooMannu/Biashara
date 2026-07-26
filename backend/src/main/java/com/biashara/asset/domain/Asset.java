package com.biashara.asset.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.AssetStatus;
import com.biashara.hr.domain.Employee;
import com.biashara.iam.domain.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Asset extends TenantAwareEntity {

    @Column(nullable = false)
    private String assetTag;

    @Column(nullable = false)
    private String name;

    /** Computers, Vehicles, Furniture, Machinery, and so on. */
    private String category;

    private String serialNumber;
    private String model;
    private String manufacturer;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal purchaseCost;

    /** Straight-line depreciation rate, percent per year. */
    @Column(precision = 5, scale = 2)
    private BigDecimal depreciationRate;

    /** Residual value at the end of useful life. */
    @Column(precision = 15, scale = 2)
    private BigDecimal salvageValue;

    private Integer usefulLifeYears;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private Employee assignedTo;

    private String location;
    private LocalDate warrantyExpiry;
    private LocalDate lastServiceDate;
    private LocalDate nextServiceDate;

    @Column(length = 500)
    private String notes;

    /**
     * Straight-line book value as at today, floored at the salvage value.
     * Computed rather than stored so it never goes stale.
     */
    public BigDecimal getCurrentValue() {
        if (depreciationRate == null || depreciationRate.signum() == 0) {
            return purchaseCost;
        }
        long days = ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
        if (days <= 0) {
            return purchaseCost;
        }
        BigDecimal years = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);
        BigDecimal annualCharge = purchaseCost.multiply(depreciationRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal accumulated = annualCharge.multiply(years).setScale(2, RoundingMode.HALF_UP);
        BigDecimal floor = salvageValue == null ? BigDecimal.ZERO : salvageValue;
        BigDecimal book = purchaseCost.subtract(accumulated);
        return book.compareTo(floor) < 0 ? floor : book;
    }

    public boolean isUnderWarranty() {
        return warrantyExpiry != null && warrantyExpiry.isAfter(LocalDate.now());
    }
}
