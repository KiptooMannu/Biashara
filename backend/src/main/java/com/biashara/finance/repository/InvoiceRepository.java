package com.biashara.finance.repository;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.common.enums.InvoiceStatus;
import com.biashara.finance.domain.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"customer"})
    Page<Invoice> findByTenantIdAndDeletedFalseOrderByIssueDateDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "sale"})
    Optional<Invoice> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, InvoiceStatus status);

    /** Unpaid invoices past their due date. */
    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            select i from Invoice i
            where i.tenant.id = :tenantId and i.deleted = false
              and i.status not in (com.biashara.common.enums.InvoiceStatus.PAID,
                                   com.biashara.common.enums.InvoiceStatus.CANCELLED)
              and i.dueDate < :today
            order by i.dueDate asc
            """)
    List<Invoice> findOverdue(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);

    /** Total still owed to the business across all open invoices. */
    @Query("""
            select coalesce(sum(i.total - coalesce(i.amountPaid, 0)), 0) from Invoice i
            where i.tenant.id = :tenantId and i.deleted = false
              and i.status not in (com.biashara.common.enums.InvoiceStatus.PAID,
                                   com.biashara.common.enums.InvoiceStatus.CANCELLED)
            """)
    BigDecimal totalOutstanding(@Param("tenantId") Long tenantId);

    @Query("""
            select cast(i.status as String) as label, sum(i.total) as value, count(i) as count
            from Invoice i
            where i.tenant.id = :tenantId and i.deleted = false
            group by i.status
            """)
    List<LabelledValue> countByStatus(@Param("tenantId") Long tenantId);
}
