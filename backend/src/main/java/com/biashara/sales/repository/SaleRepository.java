package com.biashara.sales.repository;

import com.biashara.analytics.projection.DailySeriesPoint;
import com.biashara.analytics.projection.LabelledValue;
import com.biashara.sales.domain.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = {"customer", "cashier", "branch"})
    Page<Sale> findByTenantIdAndDeletedFalseOrderBySaleDateDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "cashier", "branch", "items", "items.product"})
    Optional<Sale> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"customer", "cashier", "branch"})
    List<Sale> findTop10ByTenantIdAndDeletedFalseOrderBySaleDateDesc(Long tenantId);

    Optional<Sale> findByTenantIdAndInvoiceNumber(Long tenantId, String invoiceNumber);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    /** Sales by one cashier — the basis of the fraud/refund-pattern check. */
    @EntityGraph(attributePaths = {"customer"})
    List<Sale> findByTenantIdAndCashierIdAndDeletedFalseOrderBySaleDateDesc(Long tenantId, Long cashierId);

    // --- Aggregates ---------------------------------------------------------

    @Query("""
            select coalesce(sum(s.total), 0) from Sale s
            where s.tenant.id = :tenantId and s.deleted = false
              and s.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and s.saleDate between :from and :to
            """)
    BigDecimal sumRevenueBetween(@Param("tenantId") Long tenantId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    @Query("""
            select coalesce(sum(s.total - coalesce(s.costOfGoods, 0)), 0) from Sale s
            where s.tenant.id = :tenantId and s.deleted = false
              and s.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and s.saleDate between :from and :to
            """)
    BigDecimal sumGrossProfitBetween(@Param("tenantId") Long tenantId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query("""
            select count(s) from Sale s
            where s.tenant.id = :tenantId and s.deleted = false
              and s.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and s.saleDate between :from and :to
            """)
    long countBetween(@Param("tenantId") Long tenantId,
                      @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);

    /**
     * Daily revenue and profit series. Uses a native query because date
     * truncation is not expressible in JPQL, and grouping in the database keeps
     * this to one round trip regardless of how many sales there are.
     */
    @Query(value = """
            select to_char(s.sale_date, 'YYYY-MM-DD') as bucket,
                   sum(s.total) as value,
                   sum(s.total - coalesce(s.cost_of_goods, 0)) as secondary,
                   count(*) as count
            from sales s
            where s.tenant_id = :tenantId and s.deleted = false
              and s.status = 'COMPLETED'
              and s.sale_date >= :from
            group by to_char(s.sale_date, 'YYYY-MM-DD')
            order by bucket asc
            """, nativeQuery = true)
    List<DailySeriesPoint> dailyRevenueSeries(@Param("tenantId") Long tenantId,
                                              @Param("from") LocalDateTime from);

    /** Hour-of-day revenue distribution, for the sales heatmap. */
    @Query(value = """
            select to_char(s.sale_date, 'HH24') as label,
                   sum(s.total) as value,
                   count(*) as count
            from sales s
            where s.tenant_id = :tenantId and s.deleted = false
              and s.status = 'COMPLETED'
              and s.sale_date >= :from
            group by to_char(s.sale_date, 'HH24')
            order by label asc
            """, nativeQuery = true)
    List<LabelledValue> revenueByHour(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);

    @Query("""
            select cast(s.paymentMethod as String) as label, sum(s.total) as value, count(s) as count
            from Sale s
            where s.tenant.id = :tenantId and s.deleted = false
              and s.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and s.saleDate >= :from
            group by s.paymentMethod
            """)
    List<LabelledValue> revenueByPaymentMethod(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);

    @Query("""
            select b.name as label, sum(s.total) as value, count(s) as count
            from Sale s join s.branch b
            where s.tenant.id = :tenantId and s.deleted = false
              and s.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and s.saleDate >= :from
            group by b.name
            order by sum(s.total) desc
            """)
    List<LabelledValue> revenueByBranch(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);

    /** Revenue per cashier, for the employee-productivity widget. */
    @Query("""
            select concat(u.firstName, ' ', u.lastName) as label,
                   sum(s.total) as value,
                   count(s) as count
            from Sale s join s.cashier u
            where s.tenant.id = :tenantId and s.deleted = false
              and s.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and s.saleDate >= :from
            group by u.firstName, u.lastName
            order by sum(s.total) desc
            """)
    List<LabelledValue> revenueByCashier(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);
}
