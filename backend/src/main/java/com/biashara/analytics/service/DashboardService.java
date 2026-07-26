package com.biashara.analytics.service;

import com.biashara.ai.dto.AiDtos;
import com.biashara.ai.repository.AiInsightRepository;
import com.biashara.analytics.dto.AnalyticsDtos;
import com.biashara.analytics.projection.DailySeriesPoint;
import com.biashara.analytics.projection.LabelledValue;
import com.biashara.common.exception.NotFoundException;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.finance.repository.ExpenseRepository;
import com.biashara.finance.repository.InvoiceRepository;
import com.biashara.hr.repository.EmployeeRepository;
import com.biashara.iam.domain.Tenant;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.inventory.dto.InventoryDtos;
import com.biashara.inventory.repository.InventoryTransactionRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.notification.dto.NotificationDtos;
import com.biashara.notification.repository.NotificationRepository;
import com.biashara.procurement.repository.PurchaseRepository;
import com.biashara.sales.dto.SalesDtos;
import com.biashara.sales.repository.SaleItemRepository;
import com.biashara.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the executive dashboard.
 *
 * Every KPI carries a period-on-period comparison, because a number without a
 * direction is not information. Aggregation happens in the database rather than by
 * loading rows and looping in Java.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TenantRepository tenantRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseRepository purchaseRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final NotificationRepository notificationRepository;
    private final AiInsightRepository insightRepository;
    private final BusinessHealthService healthService;

    @Transactional(readOnly = true)
    public AnalyticsDtos.DashboardResponse build(Long tenantId, Long userId, List<String> permissions) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.of("Business", tenantId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfYesterday = startOfToday.minusDays(1);
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay();
        LocalDateTime windowStart = now.minusDays(90);

        return new AnalyticsDtos.DashboardResponse(
                tenant.getName(),
                tenant.getCurrency(),
                now,
                buildKpis(tenantId, tenant, now, startOfToday, startOfYesterday, startOfMonth, startOfLastMonth),
                mapSeries(saleRepository.dailyRevenueSeries(tenantId, windowStart)),
                mapLabelled(saleItemRepository.revenueByCategory(tenantId, now.minusDays(30))),
                mapLabelled(saleRepository.revenueByPaymentMethod(tenantId, now.minusDays(30))),
                mapLabelled(saleRepository.revenueByBranch(tenantId, now.minusDays(30))),
                mapLabelled(saleItemRepository.topProductsByRevenue(tenantId, now.minusDays(30),
                        PageRequest.of(0, 8))),
                topCustomers(tenantId),
                mapLabelled(expenseRepository.breakdownByCategory(tenantId,
                        LocalDate.now().minusDays(90), LocalDate.now())),
                mapSeries(customerRepository.dailyGrowthSeries(tenantId, windowStart)),
                mapSeries(inventoryTransactionRepository.dailyMovementSeries(tenantId, now.minusDays(30))),
                mapLabelled(saleRepository.revenueByHour(tenantId, now.minusDays(30))),
                healthService.calculate(tenantId),
                insightRepository
                        .findByTenantIdAndDismissedFalseAndDeletedFalseOrderByGeneratedAtDesc(tenantId)
                        .stream()
                        .map(AiDtos.InsightResponse::from)
                        .toList(),
                notificationRepository.findInbox(tenantId, userId, PageRequest.of(0, 12)).stream()
                        // Business-wide notifications are gated by permission.
                        .filter(notification -> notification.getRequiredPermission() == null
                                || permissions.contains(notification.getRequiredPermission()))
                        .map(NotificationDtos.NotificationResponse::from)
                        .toList(),
                productRepository.findLowStock(tenantId).stream()
                        .limit(10)
                        .map(InventoryDtos.ProductResponse::from)
                        .toList(),
                saleRepository.findTop10ByTenantIdAndDeletedFalseOrderBySaleDateDesc(tenantId).stream()
                        .map(SalesDtos.SaleResponse::summary)
                        .toList());
    }

    private List<AnalyticsDtos.KpiTile> buildKpis(Long tenantId,
                                                  Tenant tenant,
                                                  LocalDateTime now,
                                                  LocalDateTime startOfToday,
                                                  LocalDateTime startOfYesterday,
                                                  LocalDateTime startOfMonth,
                                                  LocalDateTime startOfLastMonth) {

        List<AnalyticsDtos.KpiTile> tiles = new ArrayList<>();

        // --- Today, against the same measure yesterday ------------------------
        BigDecimal today = saleRepository.sumRevenueBetween(tenantId, startOfToday, now);
        BigDecimal yesterday = saleRepository.sumRevenueBetween(tenantId, startOfYesterday, startOfToday);
        tiles.add(new AnalyticsDtos.KpiTile("todaySales", "Today's sales", today, "KES",
                changePercent(today, yesterday), true, "Compared with the whole of yesterday"));

        long ordersToday = saleRepository.countBetween(tenantId, startOfToday, now);
        long ordersYesterday = saleRepository.countBetween(tenantId, startOfYesterday, startOfToday);
        tiles.add(new AnalyticsDtos.KpiTile("ordersToday", "Orders today",
                BigDecimal.valueOf(ordersToday), "orders",
                changePercent(BigDecimal.valueOf(ordersToday), BigDecimal.valueOf(ordersYesterday)),
                true, "Completed sales recorded today"));

        // --- This month, against the same span of last month --------------------
        //
        // Compared like-for-like: month-to-date against the previous month up to the
        // same day. Comparing 26 days against a full 30-day month would show a
        // spurious decline caused only by the missing days.
        int dayOfMonth = LocalDate.now().getDayOfMonth();
        LocalDate previousMonthStart = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate previousMonthSameDay = previousMonthStart
                .withDayOfMonth(Math.min(dayOfMonth, previousMonthStart.lengthOfMonth()));
        LocalDateTime previousSpanEnd = previousMonthSameDay.atTime(now.toLocalTime());

        BigDecimal monthRevenue = saleRepository.sumRevenueBetween(tenantId, startOfMonth, now);
        BigDecimal comparableRevenue = saleRepository.sumRevenueBetween(
                tenantId, startOfLastMonth, previousSpanEnd);
        tiles.add(new AnalyticsDtos.KpiTile("monthRevenue", "Monthly revenue", monthRevenue, "KES",
                changePercent(monthRevenue, comparableRevenue), true,
                "Day 1-%d, against the same days last month".formatted(dayOfMonth)));

        BigDecimal monthProfit = saleRepository.sumGrossProfitBetween(tenantId, startOfMonth, now);
        BigDecimal comparableProfit = saleRepository.sumGrossProfitBetween(
                tenantId, startOfLastMonth, previousSpanEnd);
        tiles.add(new AnalyticsDtos.KpiTile("monthProfit", "Gross profit", monthProfit, "KES",
                changePercent(monthProfit, comparableProfit), true,
                "Revenue less cost of goods, month to date"));

        BigDecimal monthExpenses = expenseRepository.sumBetween(tenantId,
                LocalDate.now().withDayOfMonth(1), LocalDate.now());
        // Same like-for-like window as revenue, for the same reason.
        BigDecimal comparableExpenses = expenseRepository.sumBetween(tenantId,
                previousMonthStart, previousMonthSameDay);
        tiles.add(new AnalyticsDtos.KpiTile("monthExpenses", "Operating expenses", monthExpenses, "KES",
                changePercent(monthExpenses, comparableExpenses), false,
                "Day 1-%d, against the same days last month".formatted(dayOfMonth)));

        // Net position: what the business actually kept.
        BigDecimal netPosition = monthProfit.subtract(monthExpenses);
        tiles.add(new AnalyticsDtos.KpiTile("netPosition", "Net position", netPosition, "KES",
                null, true, "Gross profit less operating expenses"));

        // --- Balances and counts ----------------------------------------------
        tiles.add(new AnalyticsDtos.KpiTile("stockValue", "Inventory value",
                productRepository.totalStockValue(tenantId), "KES", null, true,
                "Stock on hand, valued at cost"));

        tiles.add(new AnalyticsDtos.KpiTile("receivables", "Owed to you",
                invoiceRepository.totalOutstanding(tenantId), "KES", null, false,
                "Unpaid customer invoices"));

        tiles.add(new AnalyticsDtos.KpiTile("products", "Products",
                BigDecimal.valueOf(productRepository.countByTenantIdAndDeletedFalse(tenantId)),
                "items", null, true, null));

        tiles.add(new AnalyticsDtos.KpiTile("lowStock", "Low stock",
                BigDecimal.valueOf(productRepository.countLowStock(tenantId)), "items", null, false,
                "At or below the minimum level"));

        tiles.add(new AnalyticsDtos.KpiTile("customers", "Customers",
                BigDecimal.valueOf(customerRepository.countByTenantIdAndDeletedFalse(tenantId)),
                "accounts", null, true, null));

        tiles.add(new AnalyticsDtos.KpiTile("employees", "Employees",
                BigDecimal.valueOf(employeeRepository.countByTenantIdAndActiveTrueAndDeletedFalse(tenantId)),
                "staff", null, true, null));

        tiles.add(new AnalyticsDtos.KpiTile("openPurchases", "Open purchase orders",
                BigDecimal.valueOf(purchaseRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, com.biashara.common.enums.PurchaseStatus.ORDERED)),
                "orders", null, false, "Ordered but not yet received"));

        // Progress against the owner's stated monthly target.
        BigDecimal target = tenant.getMonthlyRevenueTarget();
        if (target != null && target.signum() > 0) {
            BigDecimal achieved = monthRevenue.multiply(BigDecimal.valueOf(100))
                    .divide(target, 2, RoundingMode.HALF_UP);
            tiles.add(new AnalyticsDtos.KpiTile("targetProgress", "Monthly target", achieved, "%",
                    null, true, "%s of %s".formatted(
                    monthRevenue.setScale(0, RoundingMode.HALF_UP),
                    target.setScale(0, RoundingMode.HALF_UP))));
        }

        return tiles;
    }

    /** Null when there is no baseline, so the client can omit the delta entirely. */
    private BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private List<AnalyticsDtos.SeriesPoint> mapSeries(List<DailySeriesPoint> points) {
        return points.stream()
                .map(point -> new AnalyticsDtos.SeriesPoint(
                        point.getBucket(),
                        point.getValue() == null ? BigDecimal.ZERO : point.getValue(),
                        point.getSecondary() == null ? BigDecimal.ZERO : point.getSecondary(),
                        point.getCount()))
                .toList();
    }

    private List<AnalyticsDtos.LabelledValue> mapLabelled(List<LabelledValue> values) {
        return values.stream()
                .map(value -> new AnalyticsDtos.LabelledValue(
                        value.getLabel(),
                        value.getValue() == null ? BigDecimal.ZERO : value.getValue(),
                        value.getCount()))
                .toList();
    }

    private List<AnalyticsDtos.LabelledValue> topCustomers(Long tenantId) {
        return customerRepository.findTop10ByTenantIdAndDeletedFalseOrderByTotalSpentDesc(tenantId).stream()
                .filter(customer -> customer.getTotalSpent() != null && customer.getTotalSpent().signum() > 0)
                .map(customer -> new AnalyticsDtos.LabelledValue(
                        customer.getName(),
                        customer.getTotalSpent(),
                        customer.getTotalOrders() == null ? 0L : customer.getTotalOrders().longValue()))
                .toList();
    }
}
