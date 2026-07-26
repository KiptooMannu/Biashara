package com.biashara.project.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.ProjectStatus;
import com.biashara.crm.domain.Customer;
import com.biashara.iam.domain.User;
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
import java.time.LocalDate;

/** A client engagement, for MSMEs that sell services rather than stock. */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Project extends TenantAwareEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;
    private LocalDate actualEndDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal budget;

    @Column(precision = 15, scale = 2)
    private BigDecimal actualCost;

    @Column(precision = 15, scale = 2)
    private BigDecimal contractValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal amountInvoiced;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    /** Completion percentage, 0-100. */
    private Integer progress;

    public BigDecimal getProfit() {
        BigDecimal value = contractValue == null ? BigDecimal.ZERO : contractValue;
        BigDecimal cost = actualCost == null ? BigDecimal.ZERO : actualCost;
        return value.subtract(cost);
    }

    /** True when spend has passed the approved budget. */
    public boolean isOverBudget() {
        return budget != null && actualCost != null && actualCost.compareTo(budget) > 0;
    }
}
