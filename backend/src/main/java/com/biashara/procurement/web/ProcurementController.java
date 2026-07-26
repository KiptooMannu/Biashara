package com.biashara.procurement.web;

import com.biashara.common.exception.NotFoundException;
import com.biashara.iam.security.CurrentUser;
import com.biashara.inventory.domain.Product;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.procurement.dto.ProcurementDtos;
import com.biashara.procurement.repository.PurchaseRepository;
import com.biashara.procurement.repository.SupplierRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/procurement")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Procurement", description = "Suppliers, purchase orders and reorder suggestions")
public class ProcurementController {

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final CurrentUser currentUser;

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('procurement.supplier.view')")
    @Operation(summary = "List suppliers")
    public Page<ProcurementDtos.SupplierResponse> suppliers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return supplierRepository
                .findByTenantIdAndDeletedFalse(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(ProcurementDtos.SupplierResponse::from);
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('procurement.supplier.view')")
    @Operation(summary = "One supplier, with its scorecard figures")
    public ProcurementDtos.SupplierResponse supplier(@PathVariable Long id) {
        return supplierRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .map(ProcurementDtos.SupplierResponse::from)
                .orElseThrow(() -> NotFoundException.of("Supplier", id));
    }

    @GetMapping("/suppliers/scorecard")
    @PreAuthorize("hasAuthority('procurement.supplier.view')")
    @Operation(summary = "Suppliers ranked by measured reliability")
    public List<ProcurementDtos.SupplierResponse> scorecard() {
        return supplierRepository
                .findTop10ByTenantIdAndDeletedFalseOrderByReliabilityScoreDesc(currentUser.tenantId())
                .stream()
                .map(ProcurementDtos.SupplierResponse::from)
                .toList();
    }

    @GetMapping("/suppliers/underperforming")
    @PreAuthorize("hasAuthority('procurement.supplier.view')")
    @Operation(summary = "Suppliers delivering slower than their agreed lead time")
    public List<ProcurementDtos.SupplierResponse> underperforming() {
        return supplierRepository.findUnderperforming(currentUser.tenantId()).stream()
                .map(ProcurementDtos.SupplierResponse::from)
                .toList();
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasAuthority('procurement.purchase.view')")
    @Operation(summary = "Purchase orders, newest first")
    public Page<ProcurementDtos.PurchaseResponse> purchases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return purchaseRepository
                .findByTenantIdAndDeletedFalseOrderByOrderDateDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(ProcurementDtos.PurchaseResponse::summary);
    }

    @GetMapping("/purchases/{id}")
    @PreAuthorize("hasAuthority('procurement.purchase.view')")
    @Operation(summary = "One purchase order with its lines")
    public ProcurementDtos.PurchaseResponse purchase(@PathVariable Long id) {
        return purchaseRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .map(ProcurementDtos.PurchaseResponse::detail)
                .orElseThrow(() -> NotFoundException.of("Purchase order", id));
    }

    @GetMapping("/purchases/overdue")
    @PreAuthorize("hasAuthority('procurement.purchase.view')")
    @Operation(summary = "Deliveries past their expected date")
    public List<ProcurementDtos.PurchaseResponse> overdue() {
        return purchaseRepository.findOverdue(currentUser.tenantId(), LocalDate.now()).stream()
                .map(ProcurementDtos.PurchaseResponse::summary)
                .toList();
    }

    /**
     * Reorder suggestions.
     *
     * Quantity covers the supplier's lead time plus a fortnight of buffer at the
     * product's measured sales velocity, so the recommendation accounts for how
     * long the stock actually takes to arrive rather than using a fixed rule.
     */
    @GetMapping("/reorder-suggestions")
    @PreAuthorize("hasAuthority('procurement.purchase.view')")
    @Operation(summary = "Suggested purchase orders, computed from velocity and lead time")
    public List<ProcurementDtos.ReorderSuggestion> reorderSuggestions(
            @RequestParam(defaultValue = "15") int limit) {

        Long tenantId = currentUser.tenantId();
        List<ProcurementDtos.ReorderSuggestion> suggestions = new ArrayList<>();

        List<Product> candidates = productRepository.findByStockoutRisk(
                tenantId, PageRequest.of(0, Math.min(limit * 3, 150)));

        for (Product product : candidates) {
            BigDecimal daysOfCover = product.getDaysUntilStockout();
            if (daysOfCover == null) {
                continue;
            }

            int leadTime = product.getSupplier() == null || product.getSupplier().getLeadTimeDays() == null
                    ? 3
                    : product.getSupplier().getLeadTimeDays();

            // Only suggest when stock will not survive the lead time plus a week.
            if (daysOfCover.doubleValue() > leadTime + 7) {
                continue;
            }

            BigDecimal velocity = product.getSalesVelocity();
            int coverDays = leadTime + 14;
            int suggested = (int) Math.ceil(velocity.doubleValue() * coverDays)
                    - product.getCurrentStock();
            if (suggested <= 0) {
                continue;
            }

            // Round up to a sensible case quantity rather than an odd number.
            suggested = (int) (Math.ceil(suggested / 12.0) * 12);

            String urgency = daysOfCover.doubleValue() <= leadTime
                    ? "CRITICAL"
                    : (daysOfCover.doubleValue() <= leadTime + 3 ? "HIGH" : "MEDIUM");

            suggestions.add(new ProcurementDtos.ReorderSuggestion(
                    product.getId(),
                    product.getName(),
                    product.getSku(),
                    product.getCurrentStock(),
                    product.getReorderLevel(),
                    velocity,
                    daysOfCover,
                    suggested,
                    product.getBuyingPrice().multiply(BigDecimal.valueOf(suggested))
                            .setScale(2, RoundingMode.HALF_UP),
                    product.getSupplier() == null ? null : product.getSupplier().getId(),
                    product.getSupplier() == null ? "No preferred supplier" : product.getSupplier().getName(),
                    leadTime,
                    urgency,
                    "%d %s left, selling %s per day. Lead time %d days, so order now to cover %d days."
                            .formatted(product.getCurrentStock(), product.getUnit(),
                                    velocity, leadTime, coverDays)));

            if (suggestions.size() >= limit) {
                break;
            }
        }
        return suggestions;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('procurement.supplier.view')")
    @Operation(summary = "Procurement headline figures")
    public Map<String, Object> summary() {
        Long tenantId = currentUser.tenantId();
        return Map.of(
                "totalSuppliers", supplierRepository.countByTenantIdAndDeletedFalse(tenantId),
                "totalPurchaseOrders", purchaseRepository.countByTenantIdAndDeletedFalse(tenantId),
                "openOrders", purchaseRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, com.biashara.common.enums.PurchaseStatus.ORDERED),
                "overdueDeliveries", purchaseRepository.findOverdue(tenantId, LocalDate.now()).size(),
                "totalPayables", supplierRepository.totalPayables(tenantId),
                "spendThisMonth", purchaseRepository.sumPurchasesBetween(tenantId,
                        LocalDate.now().withDayOfMonth(1), LocalDate.now()));
    }
}
