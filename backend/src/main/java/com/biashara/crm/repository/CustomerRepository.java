package com.biashara.crm.repository;

import com.biashara.analytics.projection.DailySeriesPoint;
import com.biashara.analytics.projection.LabelledValue;
import com.biashara.crm.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    List<Customer> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Customer> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    @Query("""
            select c from Customer c
            where c.tenant.id = :tenantId and c.deleted = false
              and (lower(c.name) like lower(concat('%', :term, '%'))
                or lower(coalesce(c.phone, '')) like lower(concat('%', :term, '%'))
                or lower(coalesce(c.email, '')) like lower(concat('%', :term, '%')))
            """)
    Page<Customer> search(@Param("tenantId") Long tenantId, @Param("term") String term, Pageable pageable);

    List<Customer> findTop10ByTenantIdAndDeletedFalseOrderByTotalSpentDesc(Long tenantId);

    /** Customers most likely to churn, for the retention call list. */
    @Query("""
            select c from Customer c
            where c.tenant.id = :tenantId and c.deleted = false
              and c.churnRisk is not null and c.churnRisk >= :threshold
            order by c.churnRisk desc
            """)
    List<Customer> findAtRisk(@Param("tenantId") Long tenantId,
                              @Param("threshold") BigDecimal threshold,
                              Pageable pageable);

    /** Customers carrying a balance, for the receivables follow-up list. */
    @Query("""
            select c from Customer c
            where c.tenant.id = :tenantId and c.deleted = false
              and c.outstandingBalance is not null and c.outstandingBalance > 0
            order by c.outstandingBalance desc
            """)
    List<Customer> findWithOutstandingBalance(@Param("tenantId") Long tenantId);

    @Query("""
            select coalesce(sum(c.outstandingBalance), 0) from Customer c
            where c.tenant.id = :tenantId and c.deleted = false
            """)
    BigDecimal totalReceivables(@Param("tenantId") Long tenantId);

    /** Headcount per tier, for the segmentation chart. */
    @Query("""
            select cast(c.tier as String) as label, count(c) as value, count(c) as count
            from Customer c
            where c.tenant.id = :tenantId and c.deleted = false and c.tier is not null
            group by c.tier
            """)
    List<LabelledValue> countByTier(@Param("tenantId") Long tenantId);

    /** New customers per day, for the customer-growth chart. */
    @Query(value = """
            select to_char(c.created_at, 'YYYY-MM-DD') as bucket,
                   count(*) as value,
                   0 as secondary,
                   count(*) as count
            from customers c
            where c.tenant_id = :tenantId and c.deleted = false and c.created_at >= :from
            group by to_char(c.created_at, 'YYYY-MM-DD')
            order by bucket asc
            """, nativeQuery = true)
    List<DailySeriesPoint> dailyGrowthSeries(@Param("tenantId") Long tenantId,
                                             @Param("from") LocalDateTime from);
}
