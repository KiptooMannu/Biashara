package com.biashara.finance.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.InvoiceStatus;
import com.biashara.crm.domain.Customer;
import com.biashara.sales.domain.Sale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "invoices", indexes = @Index(name = "idx_invoice_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Invoice extends TenantAwareEntity {

    @Column(nullable = false)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Set when the invoice was raised from a POS sale rather than issued standalone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(precision = 15, scale = 2)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(length = 1000)
    private String notes;

    private String terms;

    public BigDecimal getBalance() {
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        return total.subtract(paid);
    }

    public boolean isOverdue() {
        return status != InvoiceStatus.PAID
                && status != InvoiceStatus.CANCELLED
                && dueDate.isBefore(LocalDate.now());
    }

    /** Positive once past due; used to bucket the receivables ageing report. */
    public long getDaysOverdue() {
        if (!isOverdue()) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, LocalDate.now());
    }
}
