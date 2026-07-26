package com.biashara.inventory.repository;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.inventory.domain.Product;
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

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    Page<Product> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    Optional<Product> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    List<Product> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Product> findByTenantIdAndSkuAndDeletedFalse(Long tenantId, String sku);

    /** POS barcode scan lookup. */
    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    Optional<Product> findByTenantIdAndBarcodeAndDeletedFalse(Long tenantId, String barcode);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    @Query("""
            select p from Product p
            where p.tenant.id = :tenantId and p.deleted = false
              and (lower(p.name) like lower(concat('%', :term, '%'))
                or lower(p.sku) like lower(concat('%', :term, '%'))
                or lower(coalesce(p.barcode, '')) like lower(concat('%', :term, '%')))
            """)
    Page<Product> search(@Param("tenantId") Long tenantId, @Param("term") String term, Pageable pageable);

    // --- Stock health -------------------------------------------------------

    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    @Query("""
            select p from Product p
            where p.tenant.id = :tenantId and p.deleted = false and p.active = true
              and p.currentStock <= p.minStock
            order by p.currentStock asc
            """)
    List<Product> findLowStock(@Param("tenantId") Long tenantId);

    @Query("""
            select count(p) from Product p
            where p.tenant.id = :tenantId and p.deleted = false and p.active = true
              and p.currentStock <= p.minStock
            """)
    long countLowStock(@Param("tenantId") Long tenantId);

    @Query("""
            select count(p) from Product p
            where p.tenant.id = :tenantId and p.deleted = false and p.active = true
              and p.currentStock <= 0
            """)
    long countOutOfStock(@Param("tenantId") Long tenantId);

    /** Total value of stock on hand, valued at cost. */
    @Query("""
            select coalesce(sum(p.buyingPrice * p.currentStock), 0) from Product p
            where p.tenant.id = :tenantId and p.deleted = false
            """)
    BigDecimal totalStockValue(@Param("tenantId") Long tenantId);

    /** Stock expiring within a window — drives the write-off warning. */
    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    @Query("""
            select p from Product p
            where p.tenant.id = :tenantId and p.deleted = false
              and p.expiryDate is not null
              and p.expiryDate between :from and :to
              and p.currentStock > 0
            order by p.expiryDate asc
            """)
    List<Product> findExpiringBetween(@Param("tenantId") Long tenantId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /**
     * Products predicted to stock out soonest, by current stock over sales
     * velocity. Only products with measurable movement are considered.
     */
    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    @Query("""
            select p from Product p
            where p.tenant.id = :tenantId and p.deleted = false and p.active = true
              and p.salesVelocity is not null and p.salesVelocity > 0
              and p.currentStock > 0
            order by (p.currentStock / p.salesVelocity) asc
            """)
    List<Product> findByStockoutRisk(@Param("tenantId") Long tenantId, Pageable pageable);

    /** Stock that has not moved: no velocity but capital tied up. */
    @EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
    @Query("""
            select p from Product p
            where p.tenant.id = :tenantId and p.deleted = false
              and (p.salesVelocity is null or p.salesVelocity = 0)
              and p.currentStock > 0
            order by (p.buyingPrice * p.currentStock) desc
            """)
    List<Product> findDeadStock(@Param("tenantId") Long tenantId, Pageable pageable);

    /** Stock value grouped by category, for the inventory composition chart. */
    @Query("""
            select c.name as label, sum(p.buyingPrice * p.currentStock) as value, count(p) as count
            from Product p join p.category c
            where p.tenant.id = :tenantId and p.deleted = false
            group by c.name
            order by sum(p.buyingPrice * p.currentStock) desc
            """)
    List<LabelledValue> stockValueByCategory(@Param("tenantId") Long tenantId);
}
