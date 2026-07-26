package com.biashara.sales.repository;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.sales.domain.SaleItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySaleId(Long saleId);

    /** Top products by revenue. Pass a Pageable to bound the result. */
    @Query("""
            select i.productName as label, sum(i.lineTotal) as value, sum(i.quantity) as count
            from SaleItem i
            where i.sale.tenant.id = :tenantId
              and i.sale.deleted = false
              and i.sale.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and i.sale.saleDate >= :from
            group by i.productName
            order by sum(i.lineTotal) desc
            """)
    List<LabelledValue> topProductsByRevenue(@Param("tenantId") Long tenantId,
                                             @Param("from") LocalDateTime from,
                                             Pageable pageable);

    @Query("""
            select i.productName as label, sum(i.quantity) as value, sum(i.quantity) as count
            from SaleItem i
            where i.sale.tenant.id = :tenantId
              and i.sale.deleted = false
              and i.sale.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and i.sale.saleDate >= :from
            group by i.productName
            order by sum(i.quantity) desc
            """)
    List<LabelledValue> topProductsByVolume(@Param("tenantId") Long tenantId,
                                            @Param("from") LocalDateTime from,
                                            Pageable pageable);

    /** Revenue split by product category, for the distribution donut. */
    @Query("""
            select c.name as label, sum(i.lineTotal) as value, sum(i.quantity) as count
            from SaleItem i join i.product p join p.category c
            where i.sale.tenant.id = :tenantId
              and i.sale.deleted = false
              and i.sale.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and i.sale.saleDate >= :from
            group by c.name
            order by sum(i.lineTotal) desc
            """)
    List<LabelledValue> revenueByCategory(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);

    /**
     * Units sold per product over a window — the input to sales-velocity.
     * The product id is cast to text so it projects onto {@code getLabel()}.
     */
    @Query("""
            select cast(p.id as String) as label, sum(i.quantity) as value, count(i) as count
            from SaleItem i join i.product p
            where i.sale.tenant.id = :tenantId
              and i.sale.deleted = false
              and i.sale.status = com.biashara.common.enums.SaleStatus.COMPLETED
              and i.sale.saleDate >= :from
            group by p.id
            """)
    List<LabelledValue> unitsSoldPerProduct(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from);

    /**
     * Products frequently bought together with a given product, by co-occurrence
     * in the same sale. This is the market-basket / cross-sell suggestion.
     *
     * Written as a cross join with a correlating predicate rather than an explicit
     * entity join, since the two sides share no mapped association.
     */
    @Query("""
            select other.productName as label, count(other) as value, count(other) as count
            from SaleItem i, SaleItem other
            where other.sale = i.sale
              and i.sale.tenant.id = :tenantId
              and i.product.id = :productId
              and other.product.id <> :productId
            group by other.productName
            order by count(other) desc
            """)
    List<LabelledValue> frequentlyBoughtWith(@Param("tenantId") Long tenantId,
                                             @Param("productId") Long productId,
                                             Pageable pageable);
}
