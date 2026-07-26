package com.biashara.inventory.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.iam.domain.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Warehouse extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Builder.Default
    @Column(nullable = false)
    private boolean defaultWarehouse = false;
}
