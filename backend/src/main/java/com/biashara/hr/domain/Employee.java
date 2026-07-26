package com.biashara.hr.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.EmploymentType;
import com.biashara.iam.domain.Branch;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An employment record.
 *
 * Deliberately separate from {@link User}: most staff on an MSME payroll never get
 * a system login, and a login (an accountant on contract, say) does not always
 * correspond to an employee. The optional {@code user} link joins the two when
 * both exist.
 */
@Entity
@Table(name = "employees", indexes = @Index(name = "idx_employee_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Employee extends TenantAwareEntity {

    @Column(nullable = false)
    private String employeeNumber;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String email;
    private String phone;
    private String nationalId;

    /** Set only when this employee also has a system login. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    private String position;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private LocalDate hireDate;

    private LocalDate contractEndDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal basicSalary;

    @Column(precision = 15, scale = 2)
    private BigDecimal allowances;

    /** 0-100, from the most recent performance review. */
    @Column(precision = 5, scale = 2)
    private BigDecimal performanceScore;

    /** Remaining annual leave days. */
    @Column(precision = 5, scale = 1)
    private BigDecimal leaveBalance;

    /** Percentage of sales value paid as commission, for sales roles. */
    @Column(precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    private String bankAccount;
    private String bankName;

    /** National Social Security Fund number. */
    private String nssfNumber;

    /** National Hospital Insurance Fund number. */
    private String nhifNumber;

    private String taxPin;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public BigDecimal getGrossSalary() {
        BigDecimal basic = basicSalary == null ? BigDecimal.ZERO : basicSalary;
        BigDecimal extra = allowances == null ? BigDecimal.ZERO : allowances;
        return basic.add(extra);
    }
}
