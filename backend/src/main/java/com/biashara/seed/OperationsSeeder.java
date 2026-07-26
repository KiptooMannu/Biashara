package com.biashara.seed;

import com.biashara.common.enums.ApprovalStatus;
import com.biashara.common.enums.AttendanceStatus;
import com.biashara.common.enums.ExpenseStatus;
import com.biashara.common.enums.InsightType;
import com.biashara.common.enums.InteractionType;
import com.biashara.common.enums.InventoryTransactionType;
import com.biashara.common.enums.InvoiceStatus;
import com.biashara.common.enums.LeaveType;
import com.biashara.common.enums.PaymentMethod;
import com.biashara.common.enums.PaymentStatus;
import com.biashara.common.enums.PurchaseStatus;
import com.biashara.common.enums.SaleStatus;
import com.biashara.common.enums.SalesChannel;
import com.biashara.crm.domain.Customer;
import com.biashara.crm.domain.CustomerInteraction;
import com.biashara.crm.repository.CustomerInteractionRepository;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.finance.domain.Expense;
import com.biashara.finance.domain.Invoice;
import com.biashara.finance.domain.Payment;
import com.biashara.finance.repository.ExpenseRepository;
import com.biashara.finance.repository.InvoiceRepository;
import com.biashara.finance.repository.PaymentRepository;
import com.biashara.hr.domain.Attendance;
import com.biashara.hr.domain.Employee;
import com.biashara.hr.domain.LeaveRequest;
import com.biashara.hr.domain.Payroll;
import com.biashara.hr.repository.AttendanceRepository;
import com.biashara.hr.repository.LeaveRequestRepository;
import com.biashara.hr.repository.PayrollRepository;
import com.biashara.iam.domain.Tenant;
import com.biashara.iam.domain.User;
import com.biashara.inventory.domain.InventoryTransaction;
import com.biashara.inventory.domain.Product;
import com.biashara.inventory.repository.InventoryTransactionRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.procurement.domain.Purchase;
import com.biashara.procurement.domain.PurchaseItem;
import com.biashara.procurement.domain.Supplier;
import com.biashara.procurement.repository.PurchaseRepository;
import com.biashara.procurement.repository.SupplierRepository;
import com.biashara.sales.domain.Sale;
import com.biashara.sales.domain.SaleItem;
import com.biashara.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stages 9-15: ninety days of trading.
 *
 * The generated history is internally consistent rather than decorative:
 *
 *  - Sales carry the cost of goods captured at the time of sale, so profit is real.
 *  - Every unit sold produces a stock-out movement, and the opening balance is
 *    back-calculated so the ledger reconciles exactly to each product's current
 *    stock. Inventory reports and the stock ledger therefore agree.
 *  - Customer totals, order counts and RFM scores are computed from the sales that
 *    were actually generated, not assigned.
 *  - Sales volume follows a weekday/weekend and month-end salary pattern, so trend
 *    charts show something a shopkeeper would recognise.
 */
@Component
@RequiredArgsConstructor
public class OperationsSeeder {

    private static final Logger log = LoggerFactory.getLogger(OperationsSeeder.class);
    private static final long RANDOM_SEED = 4051989L;
    private static final int HISTORY_DAYS = 90;

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CustomerInteractionRepository interactionRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;

    @Transactional
    public void seed(Tenant tenant,
                     IamSeeder.IamContext iam,
                     CatalogueSeeder.CatalogueContext catalogue,
                     PartnerSeeder.PartnerContext partners) {

        Random random = new Random(RANDOM_SEED);

        List<Sale> sales = seedSales(tenant, iam, catalogue, partners, random);
        Map<Long, Integer> unitsSold = tallyUnitsSold(sales);

        List<Purchase> purchases = seedPurchases(tenant, iam, catalogue, random);
        Map<Long, Integer> unitsReceived = tallyUnitsReceived(purchases);

        seedInventoryLedger(tenant, iam, catalogue, sales, unitsSold, unitsReceived, random);
        updateSalesVelocity(catalogue.products(), sales);
        updateCustomerAnalytics(partners.customers(), sales);
        seedCustomerInteractions(tenant, iam, partners, sales, random);

        List<Invoice> invoices = seedInvoices(tenant, partners, sales, random);
        seedPayments(tenant, iam, invoices, sales, random);
        seedExpenses(tenant, iam, random);
        seedAttendance(tenant, partners, random);
        seedLeaveRequests(tenant, iam, partners, random);
        seedPayroll(tenant, partners, random);
    }

    // ------------------------------------------------------------------
    // Sales
    // ------------------------------------------------------------------

    private List<Sale> seedSales(Tenant tenant,
                                 IamSeeder.IamContext iam,
                                 CatalogueSeeder.CatalogueContext catalogue,
                                 PartnerSeeder.PartnerContext partners,
                                 Random random) {

        List<Sale> sales = new ArrayList<>();
        List<Product> products = catalogue.products();
        List<Customer> customers = partners.customers();
        List<User> tills = iam.tillOperators();
        int invoiceCounter = 1;

        for (int dayOffset = HISTORY_DAYS; dayOffset >= 0; dayOffset--) {
            LocalDate day = LocalDate.now().minusDays(dayOffset);

            for (int n = 0; n < salesVolumeFor(day, dayOffset, random); n++) {
                LocalDateTime timestamp = day.atTime(tradingHour(random), random.nextInt(60));
                // Today's sales must not be stamped in the future.
                if (timestamp.isAfter(LocalDateTime.now())) {
                    timestamp = LocalDateTime.now().minusMinutes(random.nextInt(90) + 1L);
                }

                // Roughly a third of sales are to a known customer; the rest walk in.
                Customer customer = random.nextInt(3) == 0
                        ? customers.get(random.nextInt(customers.size()))
                        : null;

                Sale sale = Sale.builder()
                        .tenant(tenant)
                        .invoiceNumber(String.format("INV-%06d", invoiceCounter++))
                        .customer(customer)
                        .cashier(tills.get(random.nextInt(tills.size())))
                        .branch(iam.branches().get(weightedBranch(random, iam.branches().size())))
                        .saleDate(timestamp)
                        .status(SaleStatus.COMPLETED)
                        .channel(customer != null && "BUSINESS".equals(customer.getCustomerType())
                                ? SalesChannel.WHOLESALE
                                : SalesChannel.POS)
                        .build();

                BigDecimal subtotal = BigDecimal.ZERO;
                BigDecimal tax = BigDecimal.ZERO;
                BigDecimal cost = BigDecimal.ZERO;
                BigDecimal discount = BigDecimal.ZERO;

                int lineCount = 1 + random.nextInt(6);
                for (int line = 0; line < lineCount; line++) {
                    Product product = pickProduct(products, random);
                    int quantity = 1 + random.nextInt(product.getSellingPrice()
                            .compareTo(BigDecimal.valueOf(500)) > 0 ? 2 : 6);

                    BigDecimal unitPrice = product.getSellingPrice();
                    BigDecimal lineNet = unitPrice.multiply(BigDecimal.valueOf(quantity));

                    // Occasional line discount, as a cashier would apply.
                    BigDecimal lineDiscount = random.nextInt(12) == 0
                            ? lineNet.multiply(BigDecimal.valueOf(5))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    lineNet = lineNet.subtract(lineDiscount);

                    BigDecimal vatRate = product.getVatRate() == null ? BigDecimal.ZERO : product.getVatRate();
                    BigDecimal lineTax = lineNet.multiply(vatRate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal lineCost = product.getBuyingPrice().multiply(BigDecimal.valueOf(quantity));

                    sale.addItem(SaleItem.builder()
                            .product(product)
                            .productName(product.getName())
                            .sku(product.getSku())
                            .quantity(quantity)
                            .unitPrice(unitPrice)
                            .unitCost(product.getBuyingPrice())
                            .discount(lineDiscount)
                            .taxRate(vatRate)
                            .taxAmount(lineTax)
                            .lineTotal(lineNet.add(lineTax).setScale(2, RoundingMode.HALF_UP))
                            .returnedQuantity(0)
                            .build());

                    subtotal = subtotal.add(lineNet);
                    tax = tax.add(lineTax);
                    cost = cost.add(lineCost);
                    discount = discount.add(lineDiscount);
                }

                BigDecimal total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);
                PaymentMethod method = pickPaymentMethod(customer, random);
                boolean onCredit = method == PaymentMethod.CREDIT;

                sale.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
                sale.setTaxAmount(tax.setScale(2, RoundingMode.HALF_UP));
                sale.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
                sale.setTotal(total);
                sale.setCostOfGoods(cost.setScale(2, RoundingMode.HALF_UP));
                sale.setPaymentMethod(method);
                sale.setPaymentStatus(onCredit ? PaymentStatus.UNPAID : PaymentStatus.PAID);
                sale.setAmountPaid(onCredit ? BigDecimal.ZERO : total);
                sale.setChangeGiven(BigDecimal.ZERO);
                sale.setPaymentReference(referenceFor(method, random));

                sales.add(sale);
            }
        }

        List<Sale> saved = saleRepository.saveAll(sales);
        log.info("Seeded {} sales with {} line items over {} days",
                saved.size(),
                saved.stream().mapToInt(sale -> sale.getItems().size()).sum(),
                HISTORY_DAYS);
        return saved;
    }

    /**
     * Daily sales volume. Weekends are busier, month-end is busier still (salaries
     * land), and today is fuller so the dashboard's "orders today" is not a trickle.
     */
    private int salesVolumeFor(LocalDate day, int dayOffset, Random random) {
        // Volume is scaled so monthly revenue lands near the owner's stated target
        // and comfortably covers operating expenses. A ten-branch chain turning over
        // a few hundred thousand a month would not be solvent, and a dashboard that
        // reports a loss while scoring the business "excellent" is incoherent.
        int base = switch (day.getDayOfWeek()) {
            case SATURDAY -> 34;
            case SUNDAY -> 24;
            case FRIDAY -> 28;
            default -> 20;
        };
        // Salary week: the last three and first two days of the month.
        int dayOfMonth = day.getDayOfMonth();
        int lengthOfMonth = day.lengthOfMonth();
        if (dayOfMonth >= lengthOfMonth - 2 || dayOfMonth <= 2) {
            base += 10;
        }
        if (dayOffset == 0) {
            base += 18;
        }
        return Math.max(6, base + random.nextInt(7) - 3);
    }

    /** Trading hours 8am-8pm, with lunchtime and early-evening peaks. */
    private int tradingHour(Random random) {
        int[] weighted = {8, 9, 10, 11, 12, 12, 13, 13, 14, 15, 16, 17, 17, 18, 18, 19, 19, 20};
        return weighted[random.nextInt(weighted.length)];
    }

    /**
     * Distributes trade across the chain, weighted towards the flagship stores.
     * Every branch gets some volume so the branch-comparison report is populated
     * for all of them rather than only the first three.
     */
    private int weightedBranch(Random random, int branchCount) {
        int roll = random.nextInt(100);
        if (roll < 22) {
            return 0;
        }
        if (roll < 40) {
            return 1 % branchCount;
        }
        if (roll < 55) {
            return 2 % branchCount;
        }
        // The remaining 45% spreads evenly over the rest of the estate.
        return branchCount <= 3 ? random.nextInt(branchCount) : 3 + random.nextInt(branchCount - 3);
    }

    /**
     * Popular lines sell far more often than the long tail, which is what makes
     * "top products" and dead-stock reporting meaningful.
     */
    private Product pickProduct(List<Product> products, Random random) {
        if (random.nextInt(10) < 7) {
            // Fast movers sit at the front of the catalogue (milk, bread, staples).
            return products.get(random.nextInt(Math.min(24, products.size())));
        }
        return products.get(random.nextInt(products.size()));
    }

    private PaymentMethod pickPaymentMethod(Customer customer, Random random) {
        if (customer != null && "BUSINESS".equals(customer.getCustomerType()) && random.nextInt(3) < 2) {
            return PaymentMethod.CREDIT;
        }
        // M-Pesa dominates Kenyan retail, then cash, then card.
        int roll = random.nextInt(100);
        if (roll < 48) {
            return PaymentMethod.MPESA;
        }
        if (roll < 82) {
            return PaymentMethod.CASH;
        }
        if (roll < 94) {
            return PaymentMethod.CARD;
        }
        return PaymentMethod.BANK_TRANSFER;
    }

    private String referenceFor(PaymentMethod method, Random random) {
        return switch (method) {
            case MPESA -> "Q" + (char) ('A' + random.nextInt(26)) + random.nextInt(10)
                    + (char) ('A' + random.nextInt(26)) + random.nextInt(100000);
            case CARD -> "AUTH" + (100000 + random.nextInt(899999));
            case BANK_TRANSFER -> "FT" + (10000000 + random.nextInt(89999999));
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Inventory ledger
    // ------------------------------------------------------------------

    private Map<Long, Integer> tallyUnitsSold(List<Sale> sales) {
        Map<Long, Integer> tally = new HashMap<>();
        for (Sale sale : sales) {
            for (SaleItem item : sale.getItems()) {
                tally.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            }
        }
        return tally;
    }

    private Map<Long, Integer> tallyUnitsReceived(List<Purchase> purchases) {
        Map<Long, Integer> tally = new HashMap<>();
        for (Purchase purchase : purchases) {
            if (purchase.getReceivedDate() == null) {
                continue;
            }
            for (PurchaseItem item : purchase.getItems()) {
                int received = item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
                tally.merge(item.getProduct().getId(), received, Integer::sum);
            }
        }
        return tally;
    }

    /**
     * Writes the stock ledger.
     *
     * The opening balance is derived, not chosen: opening = current + sold + wastage
     * - received. Walking the movements forward from there lands exactly on each
     * product's recorded current stock, so the ledger and the stock figure agree.
     */
    private void seedInventoryLedger(Tenant tenant,
                                     IamSeeder.IamContext iam,
                                     CatalogueSeeder.CatalogueContext catalogue,
                                     List<Sale> sales,
                                     Map<Long, Integer> unitsSold,
                                     Map<Long, Integer> unitsReceived,
                                     Random random) {

        LocalDateTime windowStart = LocalDate.now().minusDays(HISTORY_DAYS + 1).atTime(7, 0);
        List<InventoryTransaction> movements = new ArrayList<>();
        Map<Long, Integer> running = new HashMap<>();

        // Wastage and corrections, decided up front so they can be included in the
        // opening balance calculation.
        Map<Long, Integer> wastage = new HashMap<>();
        List<InventoryTransaction> wastageMovements = new ArrayList<>();
        for (Product product : catalogue.products()) {
            if (random.nextInt(4) != 0) {
                continue;
            }
            int quantity = 1 + random.nextInt(6);
            boolean damaged = random.nextBoolean();
            wastage.merge(product.getId(), quantity, Integer::sum);
            wastageMovements.add(InventoryTransaction.builder()
                    .tenant(tenant)
                    .product(product)
                    .warehouse(product.getWarehouse())
                    .type(damaged ? InventoryTransactionType.DAMAGED : InventoryTransactionType.SHRINKAGE)
                    .quantity(quantity)
                    .unitCost(product.getBuyingPrice())
                    .reference(damaged ? "WASTE-" + product.getSku() : "COUNT-" + product.getSku())
                    .notes(damaged ? "Damaged in handling" : "Written off after stock count")
                    .performedBy(iam.storekeeper() == null ? iam.owner() : iam.storekeeper())
                    .occurredAt(LocalDate.now().minusDays(random.nextInt(HISTORY_DAYS)).atTime(16, 30))
                    .build());
        }

        // Opening balances.
        for (Product product : catalogue.products()) {
            int sold = unitsSold.getOrDefault(product.getId(), 0);
            int received = unitsReceived.getOrDefault(product.getId(), 0);
            int wasted = wastage.getOrDefault(product.getId(), 0);
            int opening = product.getCurrentStock() + sold + wasted - received;
            if (opening < 0) {
                opening = 0;
            }
            running.put(product.getId(), opening);

            movements.add(InventoryTransaction.builder()
                    .tenant(tenant)
                    .product(product)
                    .warehouse(product.getWarehouse())
                    .type(InventoryTransactionType.STOCK_IN)
                    .quantity(opening)
                    .balanceAfter(opening)
                    .unitCost(product.getBuyingPrice())
                    .reference("OPENING")
                    .notes("Opening balance at start of the reporting window")
                    .performedBy(iam.owner())
                    .occurredAt(windowStart)
                    .build());
        }

        // Sale-driven outflows, in chronological order.
        List<Sale> chronological = new ArrayList<>(sales);
        chronological.sort(Comparator.comparing(Sale::getSaleDate));
        for (Sale sale : chronological) {
            for (SaleItem item : sale.getItems()) {
                Long productId = item.getProduct().getId();
                int balance = running.getOrDefault(productId, 0) - item.getQuantity();
                running.put(productId, balance);

                movements.add(InventoryTransaction.builder()
                        .tenant(tenant)
                        .product(item.getProduct())
                        .warehouse(item.getProduct().getWarehouse())
                        .type(InventoryTransactionType.STOCK_OUT)
                        .quantity(item.getQuantity())
                        .balanceAfter(balance)
                        .unitCost(item.getUnitCost())
                        .reference(sale.getInvoiceNumber())
                        .notes("Sold at the till")
                        .performedBy(sale.getCashier())
                        .occurredAt(sale.getSaleDate())
                        .build());
            }
        }

        // Wastage, with running balances applied.
        for (InventoryTransaction movement : wastageMovements) {
            Long productId = movement.getProduct().getId();
            int balance = running.getOrDefault(productId, 0) - movement.getQuantity();
            running.put(productId, balance);
            movement.setBalanceAfter(balance);
            movements.add(movement);
        }

        inventoryTransactionRepository.saveAll(movements);
        log.info("Seeded {} inventory movements", movements.size());
    }

    /** Units sold per day over the last 30 days — the input to stockout prediction. */
    private void updateSalesVelocity(List<Product> products, List<Sale> sales) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Map<Long, Integer> recent = new HashMap<>();
        for (Sale sale : sales) {
            if (sale.getSaleDate().isBefore(since)) {
                continue;
            }
            for (SaleItem item : sale.getItems()) {
                recent.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            }
        }

        for (Product product : products) {
            int units = recent.getOrDefault(product.getId(), 0);
            product.setSalesVelocity(BigDecimal.valueOf(units)
                    .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP));
        }
        productRepository.saveAll(products);
        log.info("Recalculated sales velocity for {} products", products.size());
    }

    /**
     * Rolls sales up onto each customer and scores them.
     *
     * RFM is computed from the generated history: recency from the last purchase,
     * frequency from order count, monetary from total spend. Tier and churn risk
     * follow from those scores rather than being assigned at random.
     */
    private void updateCustomerAnalytics(List<Customer> customers, List<Sale> sales) {
        Map<Long, List<Sale>> byCustomer = new HashMap<>();
        for (Sale sale : sales) {
            if (sale.getCustomer() != null) {
                byCustomer.computeIfAbsent(sale.getCustomer().getId(), key -> new ArrayList<>()).add(sale);
            }
        }

        // Spend percentiles decide the monetary score, so tiers are relative to
        // this business rather than to an arbitrary threshold.
        List<BigDecimal> spends = new ArrayList<>();
        for (Customer customer : customers) {
            List<Sale> theirs = byCustomer.getOrDefault(customer.getId(), List.of());
            spends.add(theirs.stream().map(Sale::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        List<BigDecimal> sorted = spends.stream().sorted().toList();

        for (Customer customer : customers) {
            List<Sale> theirs = byCustomer.getOrDefault(customer.getId(), List.of());

            BigDecimal total = theirs.stream().map(Sale::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int orders = theirs.size();
            LocalDateTime last = theirs.stream().map(Sale::getSaleDate)
                    .max(Comparator.naturalOrder()).orElse(null);

            customer.setTotalSpent(total.setScale(2, RoundingMode.HALF_UP));
            customer.setTotalOrders(orders);
            customer.setAverageOrderValue(orders == 0
                    ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP));
            customer.setLastPurchaseAt(last);

            long daysSince = last == null
                    ? HISTORY_DAYS
                    : ChronoUnit.DAYS.between(last.toLocalDate(), LocalDate.now());

            int recency = daysSince <= 3 ? 5 : daysSince <= 7 ? 4 : daysSince <= 21 ? 3 : daysSince <= 45 ? 2 : 1;
            int frequency = orders >= 12 ? 5 : orders >= 8 ? 4 : orders >= 5 ? 3 : orders >= 2 ? 2 : 1;
            int monetary = percentileScore(sorted, total);

            customer.setRecencyScore(recency);
            customer.setFrequencyScore(frequency);
            customer.setMonetaryScore(monetary);

            int composite = recency + frequency + monetary;
            customer.setTier(switch (composite) {
                case 13, 14, 15 -> com.biashara.common.enums.CustomerTier.VIP;
                case 10, 11, 12 -> com.biashara.common.enums.CustomerTier.GOLD;
                case 7, 8, 9 -> com.biashara.common.enums.CustomerTier.SILVER;
                case 5, 6 -> com.biashara.common.enums.CustomerTier.BRONZE;
                case 4 -> com.biashara.common.enums.CustomerTier.DORMANT;
                default -> com.biashara.common.enums.CustomerTier.LOST;
            });

            // Churn risk is driven mainly by how long they have been away.
            BigDecimal churn = BigDecimal.valueOf(Math.min(97, daysSince * 2 + (5 - frequency) * 6));
            customer.setChurnRisk(churn.setScale(2, RoundingMode.HALF_UP));

            // Lifetime value: observed spend rate projected over 24 months.
            BigDecimal monthlyRate = total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            customer.setLifetimeValue(monthlyRate.multiply(BigDecimal.valueOf(24))
                    .setScale(2, RoundingMode.HALF_UP));
        }

        customerRepository.saveAll(customers);
        log.info("Scored {} customers from generated sales history", customers.size());
    }

    private int percentileScore(List<BigDecimal> sorted, BigDecimal value) {
        if (sorted.isEmpty() || value.signum() == 0) {
            return 1;
        }
        int rank = 0;
        for (BigDecimal candidate : sorted) {
            if (candidate.compareTo(value) <= 0) {
                rank++;
            }
        }
        double ratio = (double) rank / sorted.size();
        if (ratio > 0.8) {
            return 5;
        }
        if (ratio > 0.6) {
            return 4;
        }
        if (ratio > 0.4) {
            return 3;
        }
        if (ratio > 0.2) {
            return 2;
        }
        return 1;
    }

    /** The customer timeline: purchases plus calls, visits and complaints. */
    private void seedCustomerInteractions(Tenant tenant,
                                          IamSeeder.IamContext iam,
                                          PartnerSeeder.PartnerContext partners,
                                          List<Sale> sales,
                                          Random random) {
        List<CustomerInteraction> interactions = new ArrayList<>();

        // Most recent purchases become timeline entries.
        sales.stream()
                .filter(sale -> sale.getCustomer() != null)
                .sorted(Comparator.comparing(Sale::getSaleDate).reversed())
                .limit(60)
                .forEach(sale -> interactions.add(CustomerInteraction.builder()
                        .tenant(tenant)
                        .customer(sale.getCustomer())
                        .type(InteractionType.PURCHASE)
                        .subject("Purchase " + sale.getInvoiceNumber())
                        .notes(sale.getItems().size() + " item(s), "
                                + sale.getTotal().setScale(0, RoundingMode.HALF_UP) + " KES")
                        .reference(sale.getInvoiceNumber())
                        .handledBy(sale.getCashier())
                        .outcome("Completed")
                        .occurredAt(sale.getSaleDate())
                        .build()));

        String[][] nonPurchase = {
                {"CALL", "Follow-up call on outstanding balance", "Promised payment by Friday"},
                {"COMPLAINT", "Complaint: expired yoghurt on shelf", "Refunded and stock pulled"},
                {"VISIT", "Account visit to discuss wholesale pricing", "Agreed 3% volume discount"},
                {"EMAIL", "Sent monthly statement", "Delivered"},
                {"SMS", "Loyalty points reminder", "Redeemed in store"},
                {"FEEDBACK", "Praised the new bakery counter", "Logged"},
                {"SUPPORT_TICKET", "Query on invoice line item", "Resolved, credit note issued"},
                {"CALL", "Retention call — customer inactive 30 days", "Sent a 10% coupon"},
                {"COMPLAINT", "Long queue at the till on Saturday", "Added a second till on weekends"},
                {"VISIT", "Delivered order to premises", "Signed for by reception"},
                {"CALL", "Confirmed a bulk rice order", "Order placed"},
                {"FEEDBACK", "Requested more vegan options", "Passed to procurement"}};

        for (String[] entry : nonPurchase) {
            Customer customer = partners.customers().get(random.nextInt(partners.customers().size()));
            interactions.add(CustomerInteraction.builder()
                    .tenant(tenant)
                    .customer(customer)
                    .type(InteractionType.valueOf(entry[0]))
                    .subject(entry[1])
                    .notes(entry[2])
                    .handledBy(iam.salesManager() == null ? iam.owner() : iam.salesManager())
                    .outcome(entry[2])
                    .occurredAt(LocalDateTime.now().minusDays(random.nextInt(60)).withHour(10 + random.nextInt(8)))
                    .build());
        }

        interactionRepository.saveAll(interactions);
        log.info("Seeded {} customer interactions", interactions.size());
    }

    // ------------------------------------------------------------------
    // Procurement
    // ------------------------------------------------------------------

    private List<Purchase> seedPurchases(Tenant tenant,
                                         IamSeeder.IamContext iam,
                                         CatalogueSeeder.CatalogueContext catalogue,
                                         Random random) {
        List<Purchase> purchases = new ArrayList<>();
        List<Supplier> suppliers = catalogue.suppliers();
        List<Product> products = catalogue.products();

        for (int index = 1; index <= 50; index++) {
            Supplier supplier = suppliers.get(random.nextInt(suppliers.size()));
            LocalDate orderDate = LocalDate.now().minusDays(random.nextInt(HISTORY_DAYS));
            int leadTime = supplier.getLeadTimeDays() == null ? 4 : supplier.getLeadTimeDays();
            LocalDate expected = orderDate.plusDays(leadTime);

            // Older orders are received; recent ones are still in flight, and a few
            // are deliberately overdue so the procurement alerts have something real.
            boolean received = expected.isBefore(LocalDate.now().minusDays(2)) && random.nextInt(6) != 0;
            boolean partial = received && random.nextInt(5) == 0;

            Purchase purchase = Purchase.builder()
                    .tenant(tenant)
                    .poNumber(String.format("PO-%05d", index))
                    .supplier(supplier)
                    .orderDate(orderDate)
                    .expectedDelivery(expected)
                    .receivedDate(received ? expected.plusDays(random.nextInt(3)) : null)
                    .status(received
                            ? (partial ? PurchaseStatus.PARTIALLY_RECEIVED : PurchaseStatus.RECEIVED)
                            : (expected.isBefore(LocalDate.now()) ? PurchaseStatus.ORDERED : PurchaseStatus.ORDERED))
                    .createdBy(iam.inventoryManager() == null ? iam.owner() : iam.inventoryManager())
                    .approvedBy(iam.owner())
                    .notes(received ? "Delivered and checked in" : "Awaiting delivery")
                    .build();

            BigDecimal subtotal = BigDecimal.ZERO;
            int lines = 2 + random.nextInt(5);
            for (int line = 0; line < lines; line++) {
                Product product = products.get(random.nextInt(products.size()));
                int quantity = (1 + random.nextInt(8)) * 12;
                BigDecimal unitCost = product.getBuyingPrice();
                BigDecimal lineTotal = unitCost.multiply(BigDecimal.valueOf(quantity));

                purchase.addItem(PurchaseItem.builder()
                        .product(product)
                        .productName(product.getName())
                        .quantity(quantity)
                        .receivedQuantity(received
                                ? (partial ? (int) (quantity * 0.6) : quantity)
                                : 0)
                        .unitCost(unitCost)
                        .lineTotal(lineTotal)
                        .build());
                subtotal = subtotal.add(lineTotal);
            }

            BigDecimal tax = subtotal.multiply(new BigDecimal("16"))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal total = subtotal.add(tax);

            purchase.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
            purchase.setTaxAmount(tax);
            purchase.setTotal(total.setScale(2, RoundingMode.HALF_UP));
            purchase.setAmountPaid(received ? total.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            purchase.setPaymentStatus(received ? PaymentStatus.PAID : PaymentStatus.UNPAID);

            purchases.add(purchase);
        }

        List<Purchase> saved = purchaseRepository.saveAll(purchases);
        log.info("Seeded {} purchase orders", saved.size());
        return saved;
    }

    // ------------------------------------------------------------------
    // Finance
    // ------------------------------------------------------------------

    /** Credit sales become invoices; a spread of paid, pending and overdue. */
    private List<Invoice> seedInvoices(Tenant tenant,
                                       PartnerSeeder.PartnerContext partners,
                                       List<Sale> sales,
                                       Random random) {
        List<Invoice> invoices = new ArrayList<>();
        int counter = 1;

        List<Sale> creditSales = sales.stream()
                .filter(sale -> sale.getPaymentMethod() == PaymentMethod.CREDIT)
                .sorted(Comparator.comparing(Sale::getSaleDate).reversed())
                .limit(40)
                .toList();

        for (Sale sale : creditSales) {
            LocalDate issue = sale.getSaleDate().toLocalDate();
            LocalDate due = issue.plusDays(30);
            boolean overdue = due.isBefore(LocalDate.now());

            BigDecimal paid;
            InvoiceStatus status;
            if (overdue && random.nextInt(3) == 0) {
                paid = BigDecimal.ZERO;
                status = InvoiceStatus.OVERDUE;
            } else if (random.nextInt(3) == 0) {
                paid = sale.getTotal().multiply(BigDecimal.valueOf(40))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                status = InvoiceStatus.PARTIAL;
            } else if (random.nextInt(2) == 0) {
                paid = sale.getTotal();
                status = InvoiceStatus.PAID;
            } else {
                paid = BigDecimal.ZERO;
                status = InvoiceStatus.SENT;
            }

            invoices.add(Invoice.builder()
                    .tenant(tenant)
                    .invoiceNumber(String.format("BIL-%05d", counter++))
                    .customer(sale.getCustomer())
                    .sale(sale)
                    .issueDate(issue)
                    .dueDate(due)
                    .subtotal(sale.getSubtotal())
                    .taxAmount(sale.getTaxAmount())
                    .total(sale.getTotal())
                    .amountPaid(paid)
                    .status(status)
                    .terms("Payment due within 30 days")
                    .build());
        }

        // Guarantee a healthy invoice population even if credit sales were sparse.
        while (invoices.size() < 40) {
            Customer customer = partners.customers().get(random.nextInt(partners.customers().size()));
            BigDecimal total = BigDecimal.valueOf((5 + random.nextInt(60)) * 1000L);
            LocalDate issue = LocalDate.now().minusDays(random.nextInt(HISTORY_DAYS));
            LocalDate due = issue.plusDays(30);
            boolean overdue = due.isBefore(LocalDate.now());

            invoices.add(Invoice.builder()
                    .tenant(tenant)
                    .invoiceNumber(String.format("BIL-%05d", counter++))
                    .customer(customer)
                    .issueDate(issue)
                    .dueDate(due)
                    .subtotal(total)
                    .taxAmount(total.multiply(new BigDecimal("0.16")).setScale(2, RoundingMode.HALF_UP))
                    .total(total.multiply(new BigDecimal("1.16")).setScale(2, RoundingMode.HALF_UP))
                    .amountPaid(BigDecimal.ZERO)
                    .status(overdue ? InvoiceStatus.OVERDUE : InvoiceStatus.SENT)
                    .terms("Payment due within 30 days")
                    .build());
        }

        List<Invoice> saved = invoiceRepository.saveAll(invoices);
        log.info("Seeded {} invoices", saved.size());
        return saved;
    }

    private void seedPayments(Tenant tenant,
                              IamSeeder.IamContext iam,
                              List<Invoice> invoices,
                              List<Sale> sales,
                              Random random) {
        List<Payment> payments = new ArrayList<>();
        int counter = 1;

        // Payments against invoices that show money received.
        for (Invoice invoice : invoices) {
            if (invoice.getAmountPaid() == null || invoice.getAmountPaid().signum() == 0) {
                continue;
            }
            payments.add(Payment.builder()
                    .tenant(tenant)
                    .paymentNumber(String.format("PAY-%05d", counter++))
                    .invoice(invoice)
                    .customer(invoice.getCustomer())
                    .amount(invoice.getAmountPaid())
                    .method(random.nextBoolean() ? PaymentMethod.MPESA : PaymentMethod.BANK_TRANSFER)
                    .status(PaymentStatus.PAID)
                    .reference("RCPT" + (100000 + random.nextInt(899999)))
                    .paidAt(invoice.getIssueDate().plusDays(random.nextInt(25)).atTime(11, 30))
                    .receivedBy(iam.financeManager() == null ? iam.owner() : iam.financeManager())
                    .notes("Received against " + invoice.getInvoiceNumber())
                    .build());
        }

        // Till payments for the most recent cash and M-Pesa sales.
        sales.stream()
                .filter(sale -> sale.getPaymentMethod() != PaymentMethod.CREDIT)
                .sorted(Comparator.comparing(Sale::getSaleDate).reversed())
                .limit(60)
                .forEach(sale -> payments.add(Payment.builder()
                        .tenant(tenant)
                        .paymentNumber(String.format("PAY-%05d", payments.size() + 1000))
                        .sale(sale)
                        .customer(sale.getCustomer())
                        .amount(sale.getTotal())
                        .method(sale.getPaymentMethod())
                        .status(PaymentStatus.PAID)
                        .reference(sale.getPaymentReference())
                        .paidAt(sale.getSaleDate())
                        .receivedBy(sale.getCashier())
                        .notes("Till payment for " + sale.getInvoiceNumber())
                        .build()));

        paymentRepository.saveAll(payments);
        log.info("Seeded {} payments", payments.size());
    }

    private void seedExpenses(Tenant tenant, IamSeeder.IamContext iam, Random random) {
        /*
         * category, description, amount, recurring, department
         *
         * Scaled so total operating expenses sit well below gross profit. Payroll is
         * tracked separately on the HR side and is not double-counted here.
         */
        String[][] definitions = {
                {"Rent", "Monthly rent — Ngong Road premises", "95000", "true", "EXE"},
                {"Rent", "Monthly rent — Westlands branch", "68000", "true", "EXE"},
                {"Electricity", "KPLC bill", "34000", "true", "OPS"},
                {"Water", "Nairobi Water bill", "7500", "true", "OPS"},
                {"Internet", "Fibre and POS connectivity", "11000", "true", "IT"},
                {"Fuel", "Delivery van fuel", "24000", "false", "OPS"},
                {"Transport", "Stock collection transport", "13000", "false", "OPS"},
                {"Marketing", "Radio advertising campaign", "42000", "false", "MKT"},
                {"Marketing", "Printed flyers and posters", "12000", "false", "MKT"},
                {"Repairs", "Cold room compressor service", "28000", "false", "MNT"},
                {"Repairs", "Trolley and shelving repairs", "8500", "false", "MNT"},
                {"Insurance", "Stock and premises cover", "38000", "true", "FIN"},
                {"Office Supplies", "Receipt rolls and stationery", "6800", "false", "FIN"},
                {"Licences", "County business permit", "22000", "false", "EXE"},
                {"Security", "Guard service contract", "36000", "true", "SEC"},
                {"Cleaning", "Cleaning supplies and services", "9000", "true", "MNT"},
                {"Bank Charges", "Merchant and transaction fees", "8400", "true", "FIN"},
                {"Staff Welfare", "Staff tea and lunches", "15000", "false", "HR"},
                {"Training", "POS and customer service training", "18000", "false", "HR"},
                {"Waste Disposal", "Refuse collection", "5500", "true", "MNT"}};

        List<Expense> expenses = new ArrayList<>();
        int counter = 1;

        for (String[] entry : definitions) {
            // Recurring bills are written for each of the last three months, so the
            // cash-flow forecast has a real pattern to project forward.
            int occurrences = Boolean.parseBoolean(entry[3]) ? 3 : 1;
            for (int month = 0; month < occurrences; month++) {
                LocalDate date = LocalDate.now().minusMonths(month)
                        .withDayOfMonth(Math.min(5 + random.nextInt(20), 28));
                if (date.isAfter(LocalDate.now())) {
                    date = LocalDate.now().minusDays(random.nextInt(5) + 1);
                }
                boolean approved = date.isBefore(LocalDate.now().minusDays(3));

                expenses.add(Expense.builder()
                        .tenant(tenant)
                        .expenseNumber(String.format("EXP-%05d", counter++))
                        .category(entry[0])
                        .description(entry[1])
                        .amount(new BigDecimal(entry[2]))
                        .taxAmount(BigDecimal.ZERO)
                        .expenseDate(date)
                        .paymentMethod(random.nextBoolean() ? PaymentMethod.BANK_TRANSFER : PaymentMethod.MPESA)
                        .vendor(entry[0] + " provider")
                        .reference("REF" + (10000 + random.nextInt(89999)))
                        .status(approved ? ExpenseStatus.PAID : ExpenseStatus.PENDING)
                        .department(iam.departments().get(entry[4]))
                        .costCenter(entry[4])
                        .createdBy(iam.financeManager() == null ? iam.owner() : iam.financeManager())
                        .approvedBy(approved ? iam.owner() : null)
                        .recurring(Boolean.parseBoolean(entry[3]))
                        .recurrenceInterval(Boolean.parseBoolean(entry[3]) ? "MONTHLY" : null)
                        .build());
            }
        }

        expenseRepository.saveAll(expenses);
        log.info("Seeded {} expenses", expenses.size());
    }

    // ------------------------------------------------------------------
    // HR operations
    // ------------------------------------------------------------------

    /** Attendance for every working day in the window. */
    private void seedAttendance(Tenant tenant, PartnerSeeder.PartnerContext partners, Random random) {
        List<Attendance> records = new ArrayList<>();

        for (int dayOffset = HISTORY_DAYS; dayOffset >= 0; dayOffset--) {
            LocalDate day = LocalDate.now().minusDays(dayOffset);
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            for (Employee employee : partners.employees()) {
                int roll = random.nextInt(100);
                AttendanceStatus status;
                if (roll < 80) {
                    status = AttendanceStatus.PRESENT;
                } else if (roll < 90) {
                    status = AttendanceStatus.LATE;
                } else if (roll < 95) {
                    status = AttendanceStatus.ON_LEAVE;
                } else if (roll < 98) {
                    status = AttendanceStatus.ABSENT;
                } else {
                    status = AttendanceStatus.HALF_DAY;
                }

                LocalTime checkIn = null;
                LocalTime checkOut = null;
                BigDecimal hours = BigDecimal.ZERO;
                Integer minutesLate = null;

                if (status == AttendanceStatus.PRESENT) {
                    checkIn = LocalTime.of(7, 45 + random.nextInt(15));
                    checkOut = LocalTime.of(17, random.nextInt(40));
                    hours = BigDecimal.valueOf(8 + random.nextInt(2));
                } else if (status == AttendanceStatus.LATE) {
                    minutesLate = 10 + random.nextInt(75);
                    checkIn = LocalTime.of(8, 0).plusMinutes(minutesLate);
                    checkOut = LocalTime.of(17, random.nextInt(40));
                    hours = BigDecimal.valueOf(7);
                } else if (status == AttendanceStatus.HALF_DAY) {
                    checkIn = LocalTime.of(8, 0);
                    checkOut = LocalTime.of(12, 30);
                    hours = new BigDecimal("4.50");
                }

                records.add(Attendance.builder()
                        .tenant(tenant)
                        .employee(employee)
                        .attendanceDate(day)
                        .checkIn(checkIn)
                        .checkOut(checkOut)
                        .status(status)
                        .hoursWorked(hours)
                        .minutesLate(minutesLate)
                        .overtimeHours(status == AttendanceStatus.PRESENT && random.nextInt(10) == 0
                                ? BigDecimal.valueOf(1 + random.nextInt(3))
                                : BigDecimal.ZERO)
                        .build());
            }
        }

        attendanceRepository.saveAll(records);
        log.info("Seeded {} attendance records", records.size());
    }

    private void seedLeaveRequests(Tenant tenant,
                                   IamSeeder.IamContext iam,
                                   PartnerSeeder.PartnerContext partners,
                                   Random random) {
        List<LeaveRequest> requests = new ArrayList<>();
        LeaveType[] types = LeaveType.values();

        for (int index = 0; index < 16; index++) {
            Employee employee = partners.employees().get(random.nextInt(partners.employees().size()));
            LocalDate start = LocalDate.now().minusDays(random.nextInt(70) - 20);
            int days = 1 + random.nextInt(10);

            // Future requests are still pending; past ones have been decided.
            boolean future = start.isAfter(LocalDate.now());
            ApprovalStatus status = future
                    ? ApprovalStatus.PENDING
                    : (random.nextInt(5) == 0 ? ApprovalStatus.REJECTED : ApprovalStatus.APPROVED);

            requests.add(LeaveRequest.builder()
                    .tenant(tenant)
                    .employee(employee)
                    .leaveType(types[random.nextInt(types.length)])
                    .startDate(start)
                    .endDate(start.plusDays(days - 1L))
                    .days(BigDecimal.valueOf(days))
                    .reason(switch (random.nextInt(5)) {
                        case 0 -> "Family commitment";
                        case 1 -> "Medical appointment";
                        case 2 -> "Annual leave — travelling upcountry";
                        case 3 -> "Bereavement in the family";
                        default -> "Personal matters";
                    })
                    .status(status)
                    .approvedBy(status == ApprovalStatus.PENDING ? null
                            : (iam.hrManager() == null ? iam.owner() : iam.hrManager()))
                    .decidedAt(status == ApprovalStatus.PENDING ? null
                            : start.minusDays(2).atTime(9, 30))
                    .decisionNotes(status == ApprovalStatus.REJECTED
                            ? "Declined — insufficient cover on those dates"
                            : (status == ApprovalStatus.APPROVED ? "Approved" : null))
                    .build());
        }

        leaveRequestRepository.saveAll(requests);
        log.info("Seeded {} leave requests", requests.size());
    }

    /** Payroll for the last three months, with Kenyan statutory deductions. */
    private void seedPayroll(Tenant tenant, PartnerSeeder.PartnerContext partners, Random random) {
        List<Payroll> payrolls = new ArrayList<>();

        for (int monthsBack = 2; monthsBack >= 0; monthsBack--) {
            LocalDate month = LocalDate.now().minusMonths(monthsBack);
            String period = String.format("%d-%02d", month.getYear(), month.getMonthValue());
            boolean paid = monthsBack > 0;

            for (Employee employee : partners.employees()) {
                BigDecimal basic = employee.getBasicSalary();
                BigDecimal allowances = employee.getAllowances() == null
                        ? BigDecimal.ZERO : employee.getAllowances();
                BigDecimal overtime = random.nextInt(4) == 0
                        ? BigDecimal.valueOf(random.nextInt(6) * 500L)
                        : BigDecimal.ZERO;
                BigDecimal commission = employee.getCommissionRate() != null
                        && employee.getCommissionRate().signum() > 0
                        ? BigDecimal.valueOf(random.nextInt(12) * 1000L)
                        : BigDecimal.ZERO;

                BigDecimal gross = basic.add(allowances).add(overtime).add(commission);

                // Simplified statutory rates, enough to be recognisable.
                BigDecimal nssf = gross.min(BigDecimal.valueOf(36000))
                        .multiply(new BigDecimal("0.06")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal nhif = nhifBand(gross);
                BigDecimal taxable = gross.subtract(nssf);
                BigDecimal paye = payeFor(taxable);
                BigDecimal deductions = nssf.add(nhif).add(paye);

                payrolls.add(Payroll.builder()
                        .tenant(tenant)
                        .employee(employee)
                        .period(period)
                        .basicSalary(basic)
                        .allowances(allowances)
                        .overtimePay(overtime)
                        .commission(commission)
                        .bonus(BigDecimal.ZERO)
                        .grossPay(gross.setScale(2, RoundingMode.HALF_UP))
                        .payeTax(paye)
                        .nssfDeduction(nssf)
                        .nhifDeduction(nhif)
                        .otherDeductions(BigDecimal.ZERO)
                        .totalDeductions(deductions.setScale(2, RoundingMode.HALF_UP))
                        .netPay(gross.subtract(deductions).setScale(2, RoundingMode.HALF_UP))
                        .status(paid ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING)
                        .paidOn(paid ? month.withDayOfMonth(month.lengthOfMonth()) : null)
                        .paymentReference(paid ? "PAYROLL-" + period : null)
                        .build());
            }
        }

        payrollRepository.saveAll(payrolls);
        log.info("Seeded {} payroll records", payrolls.size());
    }

    /** Banded NHIF contribution, following the published schedule. */
    private BigDecimal nhifBand(BigDecimal gross) {
        double amount = gross.doubleValue();
        int contribution;
        if (amount < 6000) {
            contribution = 150;
        } else if (amount < 12000) {
            contribution = 400;
        } else if (amount < 25000) {
            contribution = 850;
        } else if (amount < 50000) {
            contribution = 1200;
        } else if (amount < 100000) {
            contribution = 1700;
        } else {
            contribution = 1700;
        }
        return BigDecimal.valueOf(contribution);
    }

    /** Graduated PAYE on taxable pay, less the personal relief. */
    private BigDecimal payeFor(BigDecimal taxable) {
        double amount = taxable.doubleValue();
        double tax = 0;
        double remaining = amount;

        double[][] bands = {{24000, 0.10}, {8333, 0.25}, {Double.MAX_VALUE, 0.30}};
        for (double[] band : bands) {
            if (remaining <= 0) {
                break;
            }
            double slice = Math.min(remaining, band[0]);
            tax += slice * band[1];
            remaining -= slice;
        }

        // Monthly personal relief.
        tax -= 2400;
        return BigDecimal.valueOf(Math.max(0, tax)).setScale(2, RoundingMode.HALF_UP);
    }
}
