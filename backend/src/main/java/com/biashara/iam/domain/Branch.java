package com.biashara.iam.domain;

import com.biashara.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A physical location. Sales, stock and staff are all attributable to a branch so
 * that branch-comparison reporting works even when a business has only one.
 */
@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Branch extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String location;
    private String city;
    private String phone;

    @Builder.Default
    @Column(nullable = false)
    private boolean mainBranch = false;
}
