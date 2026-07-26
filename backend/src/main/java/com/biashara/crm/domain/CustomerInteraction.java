package com.biashara.crm.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.InteractionType;
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

import java.time.LocalDateTime;

/**
 * One entry on the customer timeline. Purchases, calls, visits and complaints all
 * land here so a single query renders the complete history of a relationship.
 */
@Entity
@Table(name = "customer_interactions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CustomerInteraction extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InteractionType type;

    @Column(nullable = false)
    private String subject;

    @Column(length = 1000)
    private String notes;

    /** Links a PURCHASE interaction back to its invoice. */
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User handledBy;

    private String outcome;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
