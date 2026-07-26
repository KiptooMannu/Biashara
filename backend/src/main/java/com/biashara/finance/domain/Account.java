package com.biashara.finance.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/** A line in the chart of accounts. */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Account extends TenantAwareEntity {

    /** Conventional numeric code: 1xxx assets, 2xxx liabilities, 4xxx revenue, 5xxx expenses. */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    private String subType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Account parent;

    /** Running balance, maintained as journal entries are posted. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
