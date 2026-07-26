package com.biashara.hr.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.ApprovalStatus;
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

/** One employee's pay for one period. */
@Entity
@Table(name = "payroll")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Payroll extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Pay period in YYYY-MM form. */
    @Column(nullable = false)
    private String period;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSalary;

    @Column(precision = 15, scale = 2)
    private BigDecimal allowances;

    @Column(precision = 15, scale = 2)
    private BigDecimal overtimePay;

    @Column(precision = 15, scale = 2)
    private BigDecimal commission;

    @Column(precision = 15, scale = 2)
    private BigDecimal bonus;

    @Column(precision = 15, scale = 2)
    private BigDecimal grossPay;

    // --- Statutory deductions ----------------------------------------------

    /** Pay As You Earn income tax. */
    @Column(precision = 15, scale = 2)
    private BigDecimal payeTax;

    @Column(precision = 15, scale = 2)
    private BigDecimal nssfDeduction;

    @Column(precision = 15, scale = 2)
    private BigDecimal nhifDeduction;

    @Column(precision = 15, scale = 2)
    private BigDecimal otherDeductions;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalDeductions;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal netPay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    private LocalDate paidOn;
    private String paymentReference;
}
