package com.biashara.iam.domain;

import com.biashara.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A single atomic capability, e.g. {@code inventory.product.create}.
 *
 * Permissions — not roles — are what the API authorizes against. Every secured
 * endpoint declares {@code @PreAuthorize("hasAuthority('module.entity.action')")},
 * so a business can invent a new role at runtime without a code change.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Permission extends BaseEntity {

    /** Dot-delimited: module.entity.action */
    @Column(nullable = false, unique = true)
    private String code;

    /** Grouping for the permission-matrix UI, e.g. "Inventory". */
    @Column(nullable = false)
    private String module;

    private String description;
}
