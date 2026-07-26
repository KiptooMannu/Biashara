package com.biashara.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Payloads for the dashboard and analytics endpoints. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /**
     * One KPI tile.
     *
     * {@code higherIsBetter} exists so the client can colour a change without
     * knowing the semantics of each metric — a rise in expenses is not good news,
     * a rise in revenue is.
     */
    public record KpiTile(
            String key,
            String label,
            BigDecimal value,
            String unit,
            BigDecimal changePercent,
            boolean higherIsBetter,
            String hint) {
    }

    public record SeriesPoint(String bucket, BigDecimal value, BigDecimal secondary, Long count) {
    }

    public record LabelledValue(String label, BigDecimal value, Long count) {
    }

    /** One weighted input to the business health score. */
    public record HealthComponent(String name, BigDecimal score, BigDecimal weight, String detail) {
    }

    public record BusinessHealth(BigDecimal score, String grade, List<HealthComponent> components) {
    }

    public record DashboardResponse(
            String businessName,
            String currency,
            LocalDateTime generatedAt,
            List<KpiTile> kpis,
            List<SeriesPoint> revenueSeries,
            List<LabelledValue> revenueByCategory,
            List<LabelledValue> revenueByPaymentMethod,
            List<LabelledValue> revenueByBranch,
            List<LabelledValue> topProducts,
            List<LabelledValue> topCustomers,
            List<LabelledValue> expenseBreakdown,
            List<SeriesPoint> customerGrowth,
            List<SeriesPoint> inventoryMovement,
            List<LabelledValue> salesByHour,
            BusinessHealth health,
            List<com.biashara.ai.dto.AiDtos.InsightResponse> insights,
            List<com.biashara.notification.dto.NotificationDtos.NotificationResponse> notifications,
            List<com.biashara.inventory.dto.InventoryDtos.ProductResponse> lowStock,
            List<com.biashara.sales.dto.SalesDtos.SaleResponse> recentSales) {
    }
}
