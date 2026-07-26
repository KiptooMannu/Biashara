package com.biashara.iam.domain;

import com.biashara.common.domain.BaseEntity;
import com.biashara.common.enums.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * A business on the platform. The root of every tenant-scoped aggregate.
 */
@Entity
@Table(name = "tenants", indexes = @Index(name = "idx_tenant_slug", columnList = "slug", unique = true))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** URL-safe identifier, e.g. "greenmart" — used for tenant resolution by subdomain. */
    @Column(nullable = false, unique = true)
    private String slug;

    private String businessType;
    private String industry;
    private String location;
    private String city;
    private String country;

    @Column(nullable = false)
    private String currency;

    private String timezone;
    private String phone;
    private String email;
    private String website;
    private String logoUrl;

    /** Kenya Revenue Authority PIN — drives VAT reporting. */
    private String taxPin;

    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal defaultVatRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    private String subscriptionPlan;
    private LocalDate subscriptionStartedAt;
    private LocalDate trialEndsAt;

    private Integer maxUsers;
    private Integer maxProducts;

    /** Monthly revenue target — the Goal Progress insight measures against this. */
    @Column(precision = 15, scale = 2)
    private java.math.BigDecimal monthlyRevenueTarget;
}
