package com.biashara.finance.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.ExpenseStatus;
import com.biashara.common.enums.PaymentMethod;
import com.biashara.iam.domain.Department;
import com.biashara.iam.domain.User;
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
import java.time.LocalDate;

@Entity
@Table(name = "expenses", indexes = @Index(name = "idx_expense_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Expense extends TenantAwareEntity {

    @Column(nullable = false)
    private String expenseNumber;

    /** Rent, Electricity, Internet, Fuel, Marketing, and so on. */
    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String vendor;
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Cost centre or project code, for allocation reporting. */
    private String costCenter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    /** Recurring bills are projected forward by the cash-flow forecast. */
    @Builder.Default
    @Column(nullable = false)
    private boolean recurring = false;

    /** MONTHLY, QUARTERLY, ANNUAL — only meaningful when recurring. */
    private String recurrenceInterval;

    private String receiptUrl;

    @Column(length = 1000)
    private String notes;
}
