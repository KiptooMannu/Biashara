package com.biashara.procurement.repository;

import com.biashara.procurement.domain.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    List<PurchaseItem> findByPurchaseId(Long purchaseId);

    /**
     * Most recent unit cost paid for a product, whoever supplied it. Used to spot
     * price drift and to suggest the best supplier on a reorder.
     */
    @Query("""
            select i.unitCost from PurchaseItem i
            where i.product.id = :productId
            order by i.purchase.orderDate desc
            limit 1
            """)
    BigDecimal findLatestUnitCost(@Param("productId") Long productId);
}
