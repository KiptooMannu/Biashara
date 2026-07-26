package com.biashara.finance.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.PaymentMethod;
import com.biashara.common.enums.PaymentStatus;
import com.biashara.crm.domain.Customer;
import com.biashara.iam.domain.User;
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
import java.time.LocalDateTime;

/**
 * Money received. Attaches to an invoice, a sale, or neither (a bare account
 * deposit), which is why both links are nullable.
 */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payment_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Payment extends TenantAwareEntity {

    @Column(nullable = false)
    private String paymentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** M-Pesa code, cheque number, or bank slip reference. */
    private String reference;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_id")
    private User receivedBy;

    @Column(length = 500)
    private String notes;
}
