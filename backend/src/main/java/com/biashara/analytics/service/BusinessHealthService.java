package com.biashara.analytics.service;

import com.biashara.analytics.dto.AnalyticsDtos;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.finance.repository.ExpenseRepository;
import com.biashara.finance.repository.InvoiceRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.procurement.repository.SupplierRepository;
import com.biashara.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the business health index.
 *
 * Six weighted components, each scored 0-100 from real figures, combined into a
 * single number. Every component reports the values behind it so the owner can see
 * why the score moved rather than being handed a bare number to trust.
 */
@Service
@RequiredArgsConstructor
public class BusinessHealthService {

    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public AnalyticsDtos.BusinessHealth calculate(Long tenantId) {
        List<AnalyticsDtos.HealthComponent> components = new ArrayList<>();

        components.add(salesGrowth(tenantId));
        components.add(profitMargin(tenantId));
        components.add(stockHealth(tenantId));
        components.add(collections(tenantId));
        components.add(customerRetention(tenantId));
        components.add(supplierReliability(tenantId));

        // Weighted mean of the components.
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (AnalyticsDtos.HealthComponent component : components) {
            weightedTotal = weightedTotal.add(component.score().multiply(component.weight()));
            weightSum = weightSum.add(component.weight());
        }

        BigDecimal score = weightSum.signum() == 0
                ? BigDecimal.ZERO
                : weightedTotal.divide(weightSum, 2, RoundingMode.HALF_UP);

        return new AnalyticsDtos.BusinessHealth(score, gradeFor(score), components);
    }

    private String gradeFor(BigDecimal score) {
        double value = score.doubleValue();
        if (value >= 85) return "Excellent";
        if (value >= 70) return "Good";
        if (value >= 55) return "Fair";
        if (value >= 40) return "Needs attention";
        return "Critical";
    }

    /** This month against last month, mapped so flat trade scores 60. */
    private AnalyticsDtos.HealthComponent salesGrowth(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal current = saleRepository.sumRevenueBetween(tenantId, now.minusDays(30), now);
        BigDecimal previous = saleRepository.sumRevenueBetween(tenantId, now.minusDays(60), now.minusDays(30));

        if (previous.signum() == 0) {
            return new AnalyticsDtos.HealthComponent("Sales growth", BigDecimal.valueOf(60),
                    BigDecimal.valueOf(20), "Not enough history to compare periods");
        }

        BigDecimal change = current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);

        // -20% maps to 0, flat maps to 60, +25% and above maps to 100.
        BigDecimal score = clamp(BigDecimal.valueOf(60).add(change.multiply(BigDecimal.valueOf(1.6))));

        return new AnalyticsDtos.HealthComponent("Sales growth", score, BigDecimal.valueOf(20),
                "%s%% versus the previous 30 days".formatted(change));
    }

    /** Gross margin on completed sales, with 30% treated as a healthy retail target. */
    private AnalyticsDtos.HealthComponent profitMargin(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal revenue = saleRepository.sumRevenueBetween(tenantId, now.minusDays(30), now);
        BigDecimal profit = saleRepository.sumGrossProfitBetween(tenantId, now.minusDays(30), now);

        if (revenue.signum() == 0) {
            return new AnalyticsDtos.HealthComponent("Profit margin", BigDecimal.valueOf(50),
                    BigDecimal.valueOf(20), "No sales in the period");
        }

        BigDecimal margin = profit.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP);

        BigDecimal score = clamp(margin.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP));

        return new AnalyticsDtos.HealthComponent("Profit margin", score, BigDecimal.valueOf(20),
                "%s%% gross margin over 30 days".formatted(margin));
    }

    /** Share of active lines that are neither low nor out of stock. */
    private AnalyticsDtos.HealthComponent stockHealth(Long tenantId) {
        long total = productRepository.countByTenantIdAndDeletedFalse(tenantId);
        long low = productRepository.countLowStock(tenantId);
        long out = productRepository.countOutOfStock(tenantId);

        if (total == 0) {
            return new AnalyticsDtos.HealthComponent("Stock availability", BigDecimal.valueOf(50),
                    BigDecimal.valueOf(15), "No products yet");
        }

        // Out-of-stock is penalised twice as hard as merely low.
        double penalty = (low + out * 2.0) / total;
        BigDecimal score = clamp(BigDecimal.valueOf((1 - penalty) * 100));

        return new AnalyticsDtos.HealthComponent("Stock availability", score, BigDecimal.valueOf(15),
                "%d of %d lines low, %d out of stock".formatted(low, total, out));
    }

    /** Receivables against a month of revenue: less owed is better. */
    private AnalyticsDtos.HealthComponent collections(Long tenantId) {
        BigDecimal outstanding = invoiceRepository.totalOutstanding(tenantId);
        BigDecimal revenue = saleRepository.sumRevenueBetween(tenantId,
                LocalDateTime.now().minusDays(30), LocalDateTime.now());
        long overdue = invoiceRepository.findOverdue(tenantId, LocalDate.now()).size();

        if (revenue.signum() == 0) {
            return new AnalyticsDtos.HealthComponent("Collections", BigDecimal.valueOf(50),
                    BigDecimal.valueOf(15), "No revenue to compare against");
        }

        BigDecimal ratio = outstanding.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP);

        // Receivables at 0% of monthly revenue scores 100; at 50% or more scores 0.
        BigDecimal score = clamp(BigDecimal.valueOf(100).subtract(ratio.multiply(BigDecimal.valueOf(2))));

        return new AnalyticsDtos.HealthComponent("Collections", score, BigDecimal.valueOf(15),
                "%s owed, %d invoice(s) overdue".formatted(
                        outstanding.setScale(0, RoundingMode.HALF_UP), overdue));
    }

    /** Share of customers who are not dormant, lost or high churn risk. */
    private AnalyticsDtos.HealthComponent customerRetention(Long tenantId) {
        long total = customerRepository.countByTenantIdAndDeletedFalse(tenantId);
        if (total == 0) {
            return new AnalyticsDtos.HealthComponent("Customer retention", BigDecimal.valueOf(50),
                    BigDecimal.valueOf(15), "No customers yet");
        }

        long atRisk = customerRepository.findAtRisk(tenantId, BigDecimal.valueOf(70),
                org.springframework.data.domain.Pageable.unpaged()).size();

        BigDecimal score = clamp(BigDecimal.valueOf((1 - (double) atRisk / total) * 100));

        return new AnalyticsDtos.HealthComponent("Customer retention", score, BigDecimal.valueOf(15),
                "%d of %d customers at churn risk".formatted(atRisk, total));
    }

    /** Mean measured reliability across active suppliers. */
    private AnalyticsDtos.HealthComponent supplierReliability(Long tenantId) {
        var suppliers = supplierRepository.findByTenantIdAndDeletedFalseOrderByNameAsc(tenantId);
        var scored = suppliers.stream()
                .filter(supplier -> supplier.getReliabilityScore() != null)
                .toList();

        if (scored.isEmpty()) {
            return new AnalyticsDtos.HealthComponent("Supplier reliability", BigDecimal.valueOf(50),
                    BigDecimal.valueOf(15), "No supplier performance recorded");
        }

        BigDecimal mean = scored.stream()
                .map(supplier -> supplier.getReliabilityScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);

        long underperforming = supplierRepository.findUnderperforming(tenantId).size();

        return new AnalyticsDtos.HealthComponent("Supplier reliability", clamp(mean),
                BigDecimal.valueOf(15),
                "%s%% average on-time, %d supplier(s) drifting".formatted(mean, underperforming));
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) return BigDecimal.valueOf(100);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Exposed so the expense side of the dashboard can reuse the same window. */
    public BigDecimal monthExpenses(Long tenantId) {
        return expenseRepository.sumBetween(tenantId, LocalDate.now().minusDays(30), LocalDate.now());
    }
}
