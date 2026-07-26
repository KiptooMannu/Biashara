package com.biashara.procurement.repository;

import com.biashara.procurement.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Page<Supplier> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    List<Supplier> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Supplier> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    /** Supplier scorecard ordering — the best performers first. */
    List<Supplier> findTop10ByTenantIdAndDeletedFalseOrderByReliabilityScoreDesc(Long tenantId);

    @Query("""
            select coalesce(sum(s.outstandingBalance), 0) from Supplier s
            where s.tenant.id = :tenantId and s.deleted = false
            """)
    BigDecimal totalPayables(@Param("tenantId") Long tenantId);

    /** Suppliers whose observed delivery time has drifted past the agreed lead time. */
    @Query("""
            select s from Supplier s
            where s.tenant.id = :tenantId and s.deleted = false
              and s.averageDeliveryDays is not null and s.leadTimeDays is not null
              and s.averageDeliveryDays > s.leadTimeDays
            order by (s.averageDeliveryDays - s.leadTimeDays) desc
            """)
    List<Supplier> findUnderperforming(@Param("tenantId") Long tenantId);
}
