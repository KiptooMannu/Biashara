package com.biashara.inventory.repository;

import com.biashara.analytics.projection.DailySeriesPoint;
import com.biashara.analytics.projection.LabelledValue;
import com.biashara.inventory.domain.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @EntityGraph(attributePaths = {"product", "performedBy", "warehouse"})
    Page<InventoryTransaction> findByTenantIdAndDeletedFalseOrderByOccurredAtDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "performedBy", "warehouse"})
    List<InventoryTransaction> findByTenantIdAndProductIdAndDeletedFalseOrderByOccurredAtDesc(
            Long tenantId, Long productId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    /** Movement volume by type, for the stock-movement breakdown. */
    @Query("""
            select cast(t.type as String) as label, sum(t.quantity) as value, count(t) as count
            from InventoryTransaction t
            where t.tenant.id = :tenantId and t.deleted = false and t.occurredAt >= :from
            group by t.type
            """)
    List<LabelledValue> movementByType(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);

    /** Daily stock in versus stock out, for the inventory movement chart. */
    @Query(value = """
            select to_char(t.occurred_at, 'YYYY-MM-DD') as bucket,
                   sum(case when t.type in ('STOCK_IN', 'RETURN', 'TRANSFER_IN')
                            then t.quantity else 0 end) as value,
                   sum(case when t.type in ('STOCK_OUT', 'DAMAGED', 'TRANSFER_OUT', 'SHRINKAGE', 'EXPIRED')
                            then t.quantity else 0 end) as secondary,
                   count(*) as count
            from inventory_transactions t
            where t.tenant_id = :tenantId and t.deleted = false and t.occurred_at >= :from
            group by to_char(t.occurred_at, 'YYYY-MM-DD')
            order by bucket asc
            """, nativeQuery = true)
    List<DailySeriesPoint> dailyMovementSeries(@Param("tenantId") Long tenantId,
                                               @Param("from") LocalDateTime from);
}
