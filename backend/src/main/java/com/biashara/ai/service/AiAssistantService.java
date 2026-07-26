package com.biashara.ai.service;

import com.biashara.ai.domain.AiChatMessage;
import com.biashara.ai.dto.AiDtos;
import com.biashara.ai.repository.AiChatMessageRepository;
import com.biashara.crm.domain.Customer;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.finance.repository.ExpenseRepository;
import com.biashara.finance.repository.InvoiceRepository;
import com.biashara.hr.repository.EmployeeRepository;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.inventory.domain.Product;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.procurement.domain.Supplier;
import com.biashara.procurement.repository.SupplierRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The business assistant.
 *
 * This is deliberately not a language model. It classifies the question by intent,
 * runs the analytics queries that answer it, and composes a reply from the figures
 * it got back — so every answer is traceable to data and cannot hallucinate a
 * number. {@code dataPoints} returns the underlying values and {@code dataSource}
 * names the query, which is what makes an answer checkable.
 *
 * Swapping in Spring AI later means replacing {@link #compose} while keeping the
 * same query layer as the model's tool surface.
 */
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmployeeRepository employeeRepository;
    private final AiChatMessageRepository chatRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    /** The intents the assistant can currently answer. */
    private enum Intent {
        PROFIT_EXPLANATION,
        SALES_PERFORMANCE,
        WHO_TO_CALL,
        WHAT_TO_REORDER,
        WHAT_TO_DISCONTINUE,
        CASH_POSITION,
        TOP_PRODUCTS,
        BEST_SUPPLIER,
        EXPENSE_REVIEW,
        STAFF_COSTS,
        UNKNOWN
    }

    @Transactional
    public AiDtos.AnswerResponse ask(Long tenantId, Long userId, String question, String conversationId) {
        String thread = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;

        var tenant = tenantRepository.findById(tenantId).orElseThrow();
        var user = userRepository.findByIdAndDeletedFalse(userId).orElse(null);

        // Persist the question before answering, so the thread is intact even if
        // composing the answer fails.
        chatRepository.save(AiChatMessage.builder()
                .tenant(tenant)
                .conversationId(thread)
                .user(user)
                .role("USER")
                .content(question)
                .sentAt(LocalDateTime.now())
                .build());

        Intent intent = classify(question);
        AiDtos.AnswerResponse answer = compose(tenantId, thread, question, intent);

        chatRepository.save(AiChatMessage.builder()
                .tenant(tenant)
                .conversationId(thread)
                .user(user)
                .role("ASSISTANT")
                .content(answer.answer())
                .dataSource(answer.dataSource())
                .sentAt(LocalDateTime.now())
                .build());

        return answer;
    }

    /** Keyword-based intent matching. Crude, but honest about what it is. */
    private Intent classify(String question) {
        String text = question.toLowerCase();

        if (contains(text, "profit") && contains(text, "fall", "drop", "down", "decrease", "why", "lower")) {
            return Intent.PROFIT_EXPLANATION;
        }
        if (contains(text, "call", "contact", "follow up", "chase") && contains(text, "customer", "who", "client")) {
            return Intent.WHO_TO_CALL;
        }
        if (contains(text, "reorder", "restock", "order", "buy", "purchase", "running out", "stock out")) {
            return Intent.WHAT_TO_REORDER;
        }
        if (contains(text, "discontinue", "drop", "remove", "stop selling", "dead stock", "not selling")) {
            return Intent.WHAT_TO_DISCONTINUE;
        }
        if (contains(text, "cash", "afford", "balance", "liquidity", "money left")) {
            return Intent.CASH_POSITION;
        }
        if (contains(text, "best selling", "top product", "best product", "most sold", "bestseller")) {
            return Intent.TOP_PRODUCTS;
        }
        if (contains(text, "supplier", "vendor")) {
            return Intent.BEST_SUPPLIER;
        }
        if (contains(text, "expense", "spending", "cost", "overhead")) {
            return Intent.EXPENSE_REVIEW;
        }
        if (contains(text, "payroll", "salary", "staff cost", "wage", "employee cost")) {
            return Intent.STAFF_COSTS;
        }
        if (contains(text, "sales", "revenue", "turnover", "how much", "performance")) {
            return Intent.SALES_PERFORMANCE;
        }
        return Intent.UNKNOWN;
    }

    private boolean contains(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private AiDtos.AnswerResponse compose(Long tenantId, String thread, String question, Intent intent) {
        LocalDateTime now = LocalDateTime.now();
        List<String> dataPoints = new ArrayList<>();

        return switch (intent) {
            case PROFIT_EXPLANATION -> {
                BigDecimal revenueNow = saleRepository.sumRevenueBetween(tenantId, now.minusDays(30), now);
                BigDecimal revenueBefore = saleRepository.sumRevenueBetween(tenantId,
                        now.minusDays(60), now.minusDays(30));
                BigDecimal profitNow = saleRepository.sumGrossProfitBetween(tenantId, now.minusDays(30), now);
                BigDecimal profitBefore = saleRepository.sumGrossProfitBetween(tenantId,
                        now.minusDays(60), now.minusDays(30));
                BigDecimal expensesNow = expenseRepository.sumBetween(tenantId,
                        LocalDate.now().minusDays(30), LocalDate.now());
                BigDecimal expensesBefore = expenseRepository.sumBetween(tenantId,
                        LocalDate.now().minusDays(60), LocalDate.now().minusDays(30));

                long stockouts = productRepository.countOutOfStock(tenantId);

                dataPoints.add("Revenue, last 30 days: %s".formatted(round(revenueNow)));
                dataPoints.add("Revenue, prior 30 days: %s".formatted(round(revenueBefore)));
                dataPoints.add("Gross profit, last 30 days: %s".formatted(round(profitNow)));
                dataPoints.add("Gross profit, prior 30 days: %s".formatted(round(profitBefore)));
                dataPoints.add("Operating expenses, last 30 days: %s".formatted(round(expensesNow)));
                dataPoints.add("Lines currently out of stock: %d".formatted(stockouts));

                BigDecimal profitDelta = profitNow.subtract(profitBefore);
                BigDecimal expenseDelta = expensesNow.subtract(expensesBefore);

                StringBuilder answer = new StringBuilder();
                if (profitDelta.signum() < 0) {
                    answer.append("Gross profit is down %s against the previous 30 days. Three things are behind it:\n\n"
                            .formatted(round(profitDelta.abs())));
                    answer.append("1. Revenue moved from %s to %s.\n".formatted(
                            round(revenueBefore), round(revenueNow)));
                    answer.append("2. Operating expenses %s by %s.\n".formatted(
                            expenseDelta.signum() >= 0 ? "rose" : "fell", round(expenseDelta.abs())));
                    answer.append("3. %d product line(s) are out of stock right now, so sales that would have happened did not.\n\n"
                            .formatted(stockouts));
                    answer.append("The fastest lever is availability: clear the stockouts first, then review the expense categories that grew.");
                } else {
                    answer.append("Gross profit is actually up %s against the previous 30 days — %s versus %s.\n\n"
                            .formatted(round(profitDelta), round(profitNow), round(profitBefore)));
                    answer.append("Operating expenses %s by %s over the same period, so the improvement is %s.\n\n"
                            .formatted(expenseDelta.signum() >= 0 ? "rose" : "fell", round(expenseDelta.abs()),
                                    expenseDelta.signum() > 0 ? "partly offset by higher costs" : "genuine on both sides"));
                    answer.append("To protect it, keep the top lines in stock — %d are out of stock today.".formatted(stockouts));
                }

                yield answered(thread, question, answer.toString(), dataPoints,
                        "sales.sumGrossProfitBetween + expenses.sumBetween + products.countOutOfStock",
                        List.of("Which products should I reorder?", "What are my biggest expenses?",
                                "How is the cash position?"));
            }

            case WHO_TO_CALL -> {
                List<Customer> atRisk = customerRepository.findAtRisk(tenantId,
                        BigDecimal.valueOf(50), PageRequest.of(0, 5));
                List<Customer> owing = customerRepository.findWithOutstandingBalance(tenantId);

                StringBuilder answer = new StringBuilder("Here is your call list, ranked by what it is worth:\n\n");
                int position = 1;

                for (Customer customer : atRisk) {
                    answer.append("%d. %s — churn risk %s%%, has spent %s, last bought %s. Worth a retention call.\n"
                            .formatted(position++, customer.getName(),
                                    round0(customer.getChurnRisk()),
                                    round(customer.getTotalSpent()),
                                    customer.getLastPurchaseAt() == null
                                            ? "never" : customer.getLastPurchaseAt().toLocalDate().toString()));
                    dataPoints.add("%s: churn %s%%, spent %s".formatted(
                            customer.getName(), round0(customer.getChurnRisk()), round(customer.getTotalSpent())));
                }

                for (Customer customer : owing.stream().limit(3).toList()) {
                    answer.append("%d. %s — owes %s. Chase the payment.\n".formatted(
                            position++, customer.getName(), round(customer.getOutstandingBalance())));
                    dataPoints.add("%s owes %s".formatted(customer.getName(), round(customer.getOutstandingBalance())));
                }

                if (position == 1) {
                    answer.append("Nobody is flagged right now — no customers above the churn threshold and no outstanding balances.");
                }

                yield answered(thread, question, answer.toString(), dataPoints,
                        "customers.findAtRisk + customers.findWithOutstandingBalance",
                        List.of("Which customers owe me money?", "Who are my best customers?"));
            }

            case WHAT_TO_REORDER -> {
                List<Product> risky = productRepository.findByStockoutRisk(tenantId, PageRequest.of(0, 6));

                StringBuilder answer = new StringBuilder();
                if (risky.isEmpty()) {
                    answer.append("Nothing is at immediate risk — no product has a measurable sales velocity running down its stock.");
                } else {
                    answer.append("These need ordering, soonest first:\n\n");
                    int position = 1;
                    for (Product product : risky) {
                        BigDecimal days = product.getDaysUntilStockout();
                        int leadTime = product.getSupplier() == null
                                || product.getSupplier().getLeadTimeDays() == null
                                ? 3 : product.getSupplier().getLeadTimeDays();
                        int suggested = (int) Math.ceil(product.getSalesVelocity().doubleValue() * (leadTime + 14));

                        answer.append("%d. %s — %d %s left, selling %s a day, so about %s days of cover. "
                                        + "Lead time from %s is %d days, so order roughly %d %s now.\n"
                                .formatted(position++, product.getName(), product.getCurrentStock(),
                                        product.getUnit(), product.getSalesVelocity(), days,
                                        product.getSupplier() == null ? "your supplier" : product.getSupplier().getName(),
                                        leadTime, suggested, product.getUnit()));

                        dataPoints.add("%s: %d in stock, %s/day, %s days cover".formatted(
                                product.getName(), product.getCurrentStock(), product.getSalesVelocity(), days));
                    }
                    answer.append("\nAnything where days of cover is below the lead time is already late.");
                }

                yield answered(thread, question, answer.toString(), dataPoints,
                        "products.findByStockoutRisk (stock ÷ 30-day sales velocity)",
                        List.of("Which supplier should I use?", "What stock is not selling?"));
            }

            case WHAT_TO_DISCONTINUE -> {
                List<Product> dead = productRepository.findDeadStock(tenantId, PageRequest.of(0, 6));

                StringBuilder answer = new StringBuilder();
                if (dead.isEmpty()) {
                    answer.append("Every line with stock on hand has sold in the last 30 days. Nothing to cut.");
                } else {
                    BigDecimal tiedUp = dead.stream()
                            .map(Product::getStockValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    answer.append("These have not sold in 30 days and are holding %s of your capital:\n\n"
                            .formatted(round(tiedUp)));
                    int position = 1;
                    for (Product product : dead) {
                        answer.append("%d. %s — %d %s on the shelf, %s tied up.\n".formatted(
                                position++, product.getName(), product.getCurrentStock(),
                                product.getUnit(), round(product.getStockValue())));
                        dataPoints.add("%s: %d units, %s tied up".formatted(
                                product.getName(), product.getCurrentStock(), round(product.getStockValue())));
                    }
                    answer.append("\nDiscount them 15-20% to clear, or bundle them with a fast mover. Do not reorder until they move.");
                }

                yield answered(thread, question, answer.toString(), dataPoints,
                        "products.findDeadStock (zero sales velocity with stock on hand)",
                        List.of("What should I reorder?", "What are my best sellers?"));
            }

            case CASH_POSITION -> {
                BigDecimal revenue = saleRepository.sumRevenueBetween(tenantId, now.minusDays(30), now);
                BigDecimal expenses = expenseRepository.sumBetween(tenantId,
                        LocalDate.now().minusDays(30), LocalDate.now());
                BigDecimal payroll = employeeRepository.totalMonthlyPayroll(tenantId);
                BigDecimal receivables = invoiceRepository.totalOutstanding(tenantId);
                long overdue = invoiceRepository.findOverdue(tenantId, LocalDate.now()).size();
                BigDecimal projected = revenue.subtract(expenses).subtract(payroll);

                dataPoints.add("Revenue, 30 days: %s".formatted(round(revenue)));
                dataPoints.add("Operating expenses, 30 days: %s".formatted(round(expenses)));
                dataPoints.add("Monthly payroll commitment: %s".formatted(round(payroll)));
                dataPoints.add("Owed by customers: %s across %d overdue invoice(s)"
                        .formatted(round(receivables), overdue));

                String answer = """
                        Over the last 30 days you took in %s and spent %s on operating costs. \
                        Payroll adds a further %s each month, which leaves a projected position of %s.

                        %s is sitting in unpaid customer invoices, %d of them already overdue — that is \
                        the money to go after first, because it needs no new sales to collect.

                        %s"""
                        .formatted(round(revenue), round(expenses), round(payroll), round(projected),
                                round(receivables), overdue,
                                projected.signum() < 0
                                        ? "The projection is negative, so collections are not optional this month."
                                        : "The position is positive, so the priority is keeping it that way rather than firefighting.");

                yield answered(thread, question, answer, dataPoints,
                        "sales + expenses + employees.totalMonthlyPayroll + invoices.totalOutstanding",
                        List.of("Which customers owe me money?", "What are my biggest expenses?"));
            }

            case TOP_PRODUCTS -> {
                var top = saleItemRepository.topProductsByRevenue(tenantId, now.minusDays(30),
                        PageRequest.of(0, 5));

                StringBuilder answer = new StringBuilder("Your best sellers over the last 30 days:\n\n");
                int position = 1;
                for (var entry : top) {
                    answer.append("%d. %s — %s across %d units.\n".formatted(
                            position++, entry.getLabel(), round(entry.getValue()), entry.getCount()));
                    dataPoints.add("%s: %s, %d units".formatted(
                            entry.getLabel(), round(entry.getValue()), entry.getCount()));
                }
                answer.append("\nThese are the lines to protect: a stockout here costs more than anywhere else in the catalogue.");

                yield answered(thread, question, answer.toString(), dataPoints,
                        "saleItems.topProductsByRevenue",
                        List.of("What should I reorder?", "What is not selling?"));
            }

            case BEST_SUPPLIER -> {
                var best = supplierRepository
                        .findTop10ByTenantIdAndDeletedFalseOrderByReliabilityScoreDesc(tenantId);
                var poor = supplierRepository.findUnderperforming(tenantId);

                StringBuilder answer = new StringBuilder();
                if (best.isEmpty()) {
                    answer.append("No supplier performance has been recorded yet.");
                } else {
                    Supplier leader = best.get(0);
                    answer.append("%s is your most reliable supplier: %s%% on-time across %d orders, averaging %s days against an agreed %d.\n\n"
                            .formatted(leader.getName(), round0(leader.getReliabilityScore()),
                                    leader.getTotalOrders(), leader.getAverageDeliveryDays(),
                                    leader.getLeadTimeDays()));
                    dataPoints.add("%s: %s%% on-time".formatted(leader.getName(), round0(leader.getReliabilityScore())));

                    if (!poor.isEmpty()) {
                        answer.append("Worth watching, delivering slower than agreed:\n");
                        for (Supplier supplier : poor.stream().limit(3).toList()) {
                            answer.append("• %s — %s days actual against %d agreed.\n".formatted(
                                    supplier.getName(), supplier.getAverageDeliveryDays(), supplier.getLeadTimeDays()));
                            dataPoints.add("%s drifting: %s vs %d days".formatted(
                                    supplier.getName(), supplier.getAverageDeliveryDays(), supplier.getLeadTimeDays()));
                        }
                        answer.append("\nShift volume from the slow suppliers to %s and use it to negotiate terms."
                                .formatted(leader.getName()));
                    }
                }

                yield answered(thread, question, answer.toString(), dataPoints,
                        "suppliers reliability score and lead-time variance",
                        List.of("What should I reorder?", "How much am I spending on stock?"));
            }

            case EXPENSE_REVIEW -> {
                var breakdown = expenseRepository.breakdownByCategory(tenantId,
                        LocalDate.now().minusDays(30), LocalDate.now());
                BigDecimal total = breakdown.stream()
                        .map(entry -> entry.getValue() == null ? BigDecimal.ZERO : entry.getValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                StringBuilder answer = new StringBuilder();
                if (breakdown.isEmpty()) {
                    answer.append("No expenses have been recorded in the last 30 days.");
                } else {
                    answer.append("You spent %s over the last 30 days. Where it went:\n\n".formatted(round(total)));
                    for (var entry : breakdown.stream().limit(6).toList()) {
                        BigDecimal share = total.signum() == 0 ? BigDecimal.ZERO
                                : entry.getValue().multiply(BigDecimal.valueOf(100))
                                .divide(total, 1, RoundingMode.HALF_UP);
                        answer.append("• %s — %s (%s%% of the total)\n".formatted(
                                entry.getLabel(), round(entry.getValue()), share));
                        dataPoints.add("%s: %s".formatted(entry.getLabel(), round(entry.getValue())));
                    }
                    answer.append("\nThe top two categories are where a percentage saved is worth the most. Start there.");
                }

                yield answered(thread, question, answer.toString(), dataPoints,
                        "expenses.breakdownByCategory",
                        List.of("How is the cash position?", "What are my staff costs?"));
            }

            case STAFF_COSTS -> {
                BigDecimal payroll = employeeRepository.totalMonthlyPayroll(tenantId);
                long headcount = employeeRepository.countByTenantIdAndActiveTrueAndDeletedFalse(tenantId);
                BigDecimal revenue = saleRepository.sumRevenueBetween(tenantId, now.minusDays(30), now);
                BigDecimal ratio = revenue.signum() == 0 ? BigDecimal.ZERO
                        : payroll.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);

                dataPoints.add("Active employees: %d".formatted(headcount));
                dataPoints.add("Monthly payroll: %s".formatted(round(payroll)));
                dataPoints.add("Payroll as a share of revenue: %s%%".formatted(ratio));

                String answer = """
                        You have %d active employees costing %s a month in gross pay.

                        That is %s%% of your last 30 days of revenue. For a retail business, \
                        anything much above 20%% is worth examining — either the wage bill is heavy \
                        for the trade, or the trade is light for the team.

                        The productivity view under Reports breaks revenue down per cashier, which is \
                        where the answer usually is."""
                        .formatted(headcount, round(payroll), ratio);

                yield answered(thread, question, answer, dataPoints,
                        "employees.totalMonthlyPayroll + sales revenue",
                        List.of("How is the cash position?", "What are my biggest expenses?"));
            }

            case SALES_PERFORMANCE -> {
                BigDecimal today = saleRepository.sumRevenueBetween(tenantId,
                        LocalDate.now().atStartOfDay(), now);
                long ordersToday = saleRepository.countBetween(tenantId, LocalDate.now().atStartOfDay(), now);
                BigDecimal month = saleRepository.sumRevenueBetween(tenantId,
                        LocalDate.now().withDayOfMonth(1).atStartOfDay(), now);
                BigDecimal profit = saleRepository.sumGrossProfitBetween(tenantId,
                        LocalDate.now().withDayOfMonth(1).atStartOfDay(), now);
                BigDecimal lastMonth = saleRepository.sumRevenueBetween(tenantId,
                        now.minusDays(60), now.minusDays(30));
                BigDecimal thisPeriod = saleRepository.sumRevenueBetween(tenantId, now.minusDays(30), now);

                dataPoints.add("Today: %s across %d orders".formatted(round(today), ordersToday));
                dataPoints.add("Month to date: %s".formatted(round(month)));
                dataPoints.add("Gross profit, month to date: %s".formatted(round(profit)));

                String direction;
                if (lastMonth.signum() == 0) {
                    direction = "There is not enough history yet to compare periods.";
                } else {
                    BigDecimal change = thisPeriod.subtract(lastMonth)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(lastMonth, 1, RoundingMode.HALF_UP);
                    direction = "Against the previous 30 days, revenue is %s%s%%."
                            .formatted(change.signum() >= 0 ? "up " : "down ", change.abs());
                }

                String answer = """
                        Today you have taken %s across %d orders.

                        Month to date: %s in revenue and %s in gross profit. %s

                        The revenue chart on the dashboard shows the daily shape, and the hourly \
                        breakdown shows when your busy periods actually are."""
                        .formatted(round(today), ordersToday, round(month), round(profit), direction);

                yield answered(thread, question, answer, dataPoints,
                        "sales.sumRevenueBetween + sumGrossProfitBetween + countBetween",
                        List.of("Why did profit fall?", "What are my best sellers?"));
            }

            case UNKNOWN -> answered(thread, question, """
                    I could not match that to something I can answer from your data.

                    I work by running real queries against your business rather than guessing, so I \
                    can only answer what the figures actually support. Try one of these:

                    • Why did profits fall?
                    • Which customers should I call today?
                    • What should I reorder?
                    • Which products should I discontinue?
                    • How is my cash position?
                    • What are my biggest expenses?
                    • Which supplier is most reliable?
                    • What are my staff costs?""",
                    List.of(), "none — no matching intent",
                    List.of("Why did profits fall?", "What should I reorder?", "How is my cash position?"));
        };
    }

    private AiDtos.AnswerResponse answered(String thread, String question, String answer,
                                           List<String> dataPoints, String dataSource,
                                           List<String> followUps) {
        return new AiDtos.AnswerResponse(thread, question, answer, dataPoints, dataSource,
                followUps, LocalDateTime.now());
    }

    private String round(BigDecimal value) {
        if (value == null) {
            return "KES 0";
        }
        return "KES " + value.setScale(0, RoundingMode.HALF_UP);
    }

    private String round0(BigDecimal value) {
        return value == null ? "0" : value.setScale(0, RoundingMode.HALF_UP).toString();
    }

    @Transactional(readOnly = true)
    public List<AiDtos.ChatMessageResponse> history(Long tenantId, String conversationId) {
        return chatRepository
                .findByTenantIdAndConversationIdAndDeletedFalseOrderBySentAtAsc(tenantId, conversationId)
                .stream()
                .map(message -> new AiDtos.ChatMessageResponse(
                        message.getId(), message.getConversationId(), message.getRole(),
                        message.getContent(), message.getSentAt()))
                .toList();
    }

    /** Starter prompts shown on an empty assistant screen. */
    public List<String> suggestedQuestions() {
        return List.of(
                "Why did profits fall?",
                "Which customers should I call today?",
                "What should I reorder this week?",
                "Which products should I discontinue?",
                "How is my cash position?",
                "What are my biggest expenses?",
                "Which supplier is most reliable?",
                "What are my staff costs?",
                "How are sales performing?");
    }

    /** Highest-value customers, used by the CRM panel alongside the assistant. */
    @Transactional(readOnly = true)
    public List<String> topCustomerNames(Long tenantId) {
        return customerRepository.findTop10ByTenantIdAndDeletedFalseOrderByTotalSpentDesc(tenantId).stream()
                .sorted(Comparator.comparing(Customer::getTotalSpent).reversed())
                .map(Customer::getName)
                .toList();
    }
}
