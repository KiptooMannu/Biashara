package com.biashara.analytics.web;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.hr.repository.AttendanceRepository;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.security.CurrentUser;
import com.biashara.inventory.repository.InventoryTransactionRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.sales.repository.SaleItemRepository;
import com.biashara.sales.repository.SaleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Analytical reports across the business")
public class ReportController {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryTransactionRepository movementRepository;
    private final AttendanceRepository attendanceRepository;
    private final TenantRepository tenantRepository;
    private final CurrentUser currentUser;

    /** Everything the reports screen needs, so it renders in one round trip. */
    @GetMapping
    @PreAuthorize("hasAuthority('report.view')")
    @Operation(summary = "The report catalogue with its data")
    public Map<String, Object> reports(@RequestParam(defaultValue = "30") int days) {
        Long tenantId = currentUser.tenantId();
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        LocalDate fromDate = LocalDate.now().minusDays(days);

        Map<String, Object> reports = new java.util.LinkedHashMap<>();
        reports.put("periodDays", days);
        reports.put("currency", tenantRepository.findById(tenantId)
                .map(tenant -> tenant.getCurrency()).orElse("KES"));

        reports.put("revenueTrend", saleRepository.dailyRevenueSeries(tenantId, from).stream()
                .map(point -> Map.<String, Object>of(
                        "bucket", point.getBucket(),
                        "value", point.getValue() == null ? BigDecimal.ZERO : point.getValue(),
                        "secondary", point.getSecondary() == null ? BigDecimal.ZERO : point.getSecondary(),
                        "count", point.getCount()))
                .toList());

        reports.put("topProductsByRevenue", labelled(
                saleItemRepository.topProductsByRevenue(tenantId, from, PageRequest.of(0, 15))));
        reports.put("topProductsByVolume", labelled(
                saleItemRepository.topProductsByVolume(tenantId, from, PageRequest.of(0, 15))));
        reports.put("revenueByCategory", labelled(saleItemRepository.revenueByCategory(tenantId, from)));
        reports.put("revenueByBranch", labelled(saleRepository.revenueByBranch(tenantId, from)));
        reports.put("revenueByCashier", labelled(saleRepository.revenueByCashier(tenantId, from)));
        reports.put("revenueByHour", labelled(saleRepository.revenueByHour(tenantId, from)));
        reports.put("paymentMix", labelled(saleRepository.revenueByPaymentMethod(tenantId, from)));
        reports.put("stockValueByCategory", labelled(productRepository.stockValueByCategory(tenantId)));
        reports.put("customerTiers", labelled(customerRepository.countByTier(tenantId)));
        reports.put("stockMovementByType", labelled(movementRepository.movementByType(tenantId, from)));
        reports.put("attendanceMix", labelled(
                attendanceRepository.breakdownByStatus(tenantId, fromDate, LocalDate.now())));

        reports.put("abcAnalysis", abcAnalysis(tenantId, from));

        return reports;
    }

    /**
     * ABC inventory analysis.
     *
     * Ranks products by revenue contribution and splits them into the classes the
     * technique defines: A is the top 80% of revenue, B the next 15%, C the rest.
     * The point is that class A deserves tight control and class C does not.
     */
    @GetMapping("/abc-analysis")
    @PreAuthorize("hasAuthority('report.view')")
    @Operation(summary = "ABC inventory classification by revenue contribution")
    public List<Map<String, Object>> abcAnalysis(@RequestParam(defaultValue = "90") int days) {
        return abcAnalysis(currentUser.tenantId(), LocalDateTime.now().minusDays(days));
    }

    private List<Map<String, Object>> abcAnalysis(Long tenantId, LocalDateTime from) {
        List<LabelledValue> ranked = saleItemRepository.topProductsByRevenue(
                tenantId, from, PageRequest.of(0, 500));

        BigDecimal total = ranked.stream()
                .map(entry -> entry.getValue() == null ? BigDecimal.ZERO : entry.getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> classified = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;

        for (LabelledValue entry : ranked) {
            BigDecimal value = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            cumulative = cumulative.add(value);

            BigDecimal cumulativeShare = total.signum() == 0
                    ? BigDecimal.ZERO
                    : cumulative.multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);

            String classification = cumulativeShare.doubleValue() <= 80
                    ? "A"
                    : (cumulativeShare.doubleValue() <= 95 ? "B" : "C");

            classified.add(Map.of(
                    "product", entry.getLabel(),
                    "revenue", value,
                    "units", entry.getCount() == null ? 0L : entry.getCount(),
                    "cumulativeShare", cumulativeShare,
                    "classification", classification));
        }
        return classified;
    }

    private List<Map<String, Object>> labelled(List<LabelledValue> values) {
        return values.stream()
                .map(value -> Map.<String, Object>of(
                        "label", value.getLabel() == null ? "Unknown" : value.getLabel(),
                        "value", value.getValue() == null ? BigDecimal.ZERO : value.getValue(),
                        "count", value.getCount() == null ? 0L : value.getCount()))
                .toList();
    }
}
