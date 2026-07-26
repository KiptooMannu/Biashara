package com.biashara.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Surrogate key plus create/update auditing and a soft-delete flag.
 *
 * Deletes across BIASHARA are soft by policy: an ERP has to be able to answer
 * "what did this invoice look like before it was voided", and audit logs must be
 * able to reference rows that a user has "deleted".
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEntity {

    /**
     * Sequence-backed rather than IDENTITY, deliberately.
     *
     * IDENTITY forces Hibernate to round-trip for every single insert to read the
     * generated key back, which silently disables JDBC batching. Seeding ninety
     * days of trading meant ~12,000 individual round trips to a remote database.
     * A pooled sequence hands out 50 ids per call, so inserts batch and the same
     * seed completes in a fraction of the time.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "biashara_id_seq")
    @SequenceGenerator(name = "biashara_id_seq", sequenceName = "biashara_id_seq", allocationSize = 50)
    private Long id;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    public boolean isNew() {
        return id == null;
    }
}
