package com.biashara.iam.domain;

import com.biashara.common.domain.TenantAwareEntity;
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

/**
 * An organisational unit. Department managers may only create users within the
 * department they head — see {@code UserManagementService}.
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Department extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_user_id")
    private User head;

    @Column(precision = 15, scale = 2)
    private java.math.BigDecimal monthlyBudget;
}
