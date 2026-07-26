package com.biashara.asset.repository;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.asset.domain.Asset;
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

public interface AssetRepository extends JpaRepository<Asset, Long> {

    @EntityGraph(attributePaths = {"assignedTo", "branch"})
    Page<Asset> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedTo", "branch"})
    List<Asset> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Asset> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    /** Original cost of the asset register; book value is computed per asset. */
    @Query("""
            select coalesce(sum(a.purchaseCost), 0) from Asset a
            where a.tenant.id = :tenantId and a.deleted = false
            """)
    BigDecimal totalPurchaseCost(@Param("tenantId") Long tenantId);

    /** Assets due for service, so maintenance can be scheduled before failure. */
    @EntityGraph(attributePaths = {"assignedTo"})
    List<Asset> findByTenantIdAndNextServiceDateBeforeAndDeletedFalseOrderByNextServiceDateAsc(
            Long tenantId, LocalDate before);

    @Query("""
            select a.category as label, sum(a.purchaseCost) as value, count(a) as count
            from Asset a
            where a.tenant.id = :tenantId and a.deleted = false
            group by a.category
            order by sum(a.purchaseCost) desc
            """)
    List<LabelledValue> valueByCategory(@Param("tenantId") Long tenantId);
}
