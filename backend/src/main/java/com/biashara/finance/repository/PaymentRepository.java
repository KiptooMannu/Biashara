package com.biashara.finance.repository;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.finance.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"customer", "invoice", "receivedBy"})
    Page<Payment> findByTenantIdAndDeletedFalseOrderByPaidAtDesc(Long tenantId, Pageable pageable);

    List<Payment> findByTenantIdAndInvoiceIdAndDeletedFalse(Long tenantId, Long invoiceId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.tenant.id = :tenantId and p.deleted = false
              and p.paidAt between :from and :to
            """)
    BigDecimal sumBetween(@Param("tenantId") Long tenantId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    /** Cash collected per method — shows how customers actually pay. */
    @Query("""
            select cast(p.method as String) as label, sum(p.amount) as value, count(p) as count
            from Payment p
            where p.tenant.id = :tenantId and p.deleted = false and p.paidAt >= :from
            group by p.method
            order by sum(p.amount) desc
            """)
    List<LabelledValue> breakdownByMethod(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);
}
