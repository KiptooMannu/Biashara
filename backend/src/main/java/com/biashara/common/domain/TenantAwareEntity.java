package com.biashara.common.domain;

import com.biashara.iam.domain.Tenant;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base class for every row that belongs to exactly one business.
 *
 * BIASHARA uses shared-schema multi-tenancy with a discriminator column: one
 * deployment, one set of tables, every tenant-owned row carrying tenant_id. Every
 * repository query below is scoped by tenant, and {@code TenantContext} carries
 * the current tenant for the request so services never have to be trusted to
 * remember it.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class TenantAwareEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
