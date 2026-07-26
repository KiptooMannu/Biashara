package com.biashara.procurement.repository;

import com.biashara.common.enums.PurchaseStatus;
import com.biashara.procurement.domain.Purchase;
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

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @EntityGraph(attributePaths = {"supplier", "createdBy"})
    Page<Purchase> findByTenantIdAndDeletedFalseOrderByOrderDateDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "items", "items.product", "createdBy"})
    Optional<Purchase> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"supplier", "createdBy"})
    List<Purchase> findTop10ByTenantIdAndDeletedFalseOrderByOrderDateDesc(Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, PurchaseStatus status);

    /** Orders past their expected delivery date and still not received. */
    @EntityGraph(attributePaths = {"supplier", "createdBy"})
    @Query("""
            select p from Purchase p
            where p.tenant.id = :tenantId and p.deleted = false
              and p.receivedDate is null
              and p.status <> com.biashara.common.enums.PurchaseStatus.CANCELLED
              and p.expectedDelivery < :today
            order by p.expectedDelivery asc
            """)
    List<Purchase> findOverdue(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);

    @Query("""
            select coalesce(sum(p.total), 0) from Purchase p
            where p.tenant.id = :tenantId and p.deleted = false
              and p.orderDate between :from and :to
            """)
    BigDecimal sumPurchasesBetween(@Param("tenantId") Long tenantId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
