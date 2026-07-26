package com.biashara.sales.web;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.common.exception.NotFoundException;
import com.biashara.iam.security.CurrentUser;
import com.biashara.sales.dto.SalesDtos;
import com.biashara.sales.repository.SaleItemRepository;
import com.biashara.sales.repository.SaleRepository;
import com.biashara.sales.service.PosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Sales & POS", description = "Point of sale, sales history and receipts")
public class SalesController {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PosService posService;
    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('sales.sale.view')")
    @Operation(summary = "Sales history, newest first")
    public Page<SalesDtos.SaleResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return saleRepository
                .findByTenantIdAndDeletedFalseOrderBySaleDateDesc(
                        currentUser.tenantId(), PageRequest.of(page, Math.min(size, 200)))
                .map(SalesDtos.SaleResponse::summary);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales.sale.view')")
    @Operation(summary = "One sale, with its line items — the receipt view")
    public SalesDtos.SaleResponse detail(@PathVariable Long id) {
        return saleRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .map(SalesDtos.SaleResponse::detail)
                .orElseThrow(() -> NotFoundException.of("Sale", id));
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('sales.pos.operate')")
    @Operation(summary = "Record a sale at the till")
    public SalesDtos.SaleResponse checkout(@Valid @RequestBody SalesDtos.CheckoutRequest request) {
        return posService.checkout(currentUser.tenantId(), currentUser.userId(), request);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('sales.sale.view')")
    @Operation(summary = "Headline sales figures for today and the month")
    public Map<String, Object> summary() {
        Long tenantId = currentUser.tenantId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return Map.of(
                "todayRevenue", saleRepository.sumRevenueBetween(tenantId, startOfToday, now),
                "todayOrders", saleRepository.countBetween(tenantId, startOfToday, now),
                "monthRevenue", saleRepository.sumRevenueBetween(tenantId, startOfMonth, now),
                "monthProfit", saleRepository.sumGrossProfitBetween(tenantId, startOfMonth, now),
                "monthOrders", saleRepository.countBetween(tenantId, startOfMonth, now),
                "totalSales", saleRepository.countByTenantIdAndDeletedFalse(tenantId));
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAuthority('sales.sale.view')")
    @Operation(summary = "Best sellers by revenue over a window")
    public List<Map<String, Object>> topProducts(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return saleItemRepository
                .topProductsByRevenue(currentUser.tenantId(),
                        LocalDateTime.now().minusDays(days), PageRequest.of(0, Math.min(limit, 50)))
                .stream()
                .map(SalesController::toMap)
                .toList();
    }

    /** Market-basket companions for one product — the cross-sell suggestion. */
    @GetMapping("/products/{productId}/bought-with")
    @PreAuthorize("hasAuthority('sales.sale.view')")
    @Operation(summary = "Products frequently bought with this one")
    public List<Map<String, Object>> boughtWith(@PathVariable Long productId,
                                                @RequestParam(defaultValue = "5") int limit) {
        return saleItemRepository
                .frequentlyBoughtWith(currentUser.tenantId(), productId, PageRequest.of(0, Math.min(limit, 20)))
                .stream()
                .map(SalesController::toMap)
                .toList();
    }

    @GetMapping("/by-hour")
    @PreAuthorize("hasAuthority('sales.sale.view')")
    @Operation(summary = "Revenue distribution by hour of day")
    public List<Map<String, Object>> byHour(@RequestParam(defaultValue = "30") int days) {
        return saleRepository.revenueByHour(currentUser.tenantId(), LocalDateTime.now().minusDays(days))
                .stream()
                .map(SalesController::toMap)
                .toList();
    }

    @GetMapping("/by-cashier")
    @PreAuthorize("hasAuthority('sales.sale.view')")
    @Operation(summary = "Revenue per cashier — employee productivity")
    public List<Map<String, Object>> byCashier(@RequestParam(defaultValue = "30") int days) {
        return saleRepository.revenueByCashier(currentUser.tenantId(), LocalDateTime.now().minusDays(days))
                .stream()
                .map(SalesController::toMap)
                .toList();
    }

    /** Projections carry nulls when a group is empty; normalise for the client. */
    private static Map<String, Object> toMap(LabelledValue value) {
        return Map.of(
                "label", value.getLabel() == null ? "Unknown" : value.getLabel(),
                "value", value.getValue() == null ? java.math.BigDecimal.ZERO : value.getValue(),
                "count", value.getCount() == null ? 0L : value.getCount());
    }
}
