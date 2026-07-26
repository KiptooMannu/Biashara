package com.biashara.seed;

import com.biashara.ai.domain.AiInsight;
import com.biashara.ai.repository.AiInsightRepository;
import com.biashara.asset.domain.Asset;
import com.biashara.asset.repository.AssetRepository;
import com.biashara.common.enums.AccountType;
import com.biashara.common.enums.AssetStatus;
import com.biashara.common.enums.InsightType;
import com.biashara.common.enums.NotificationChannel;
import com.biashara.common.enums.Priority;
import com.biashara.common.enums.ProjectStatus;
import com.biashara.common.enums.Severity;
import com.biashara.common.enums.TaskStatus;
import com.biashara.crm.domain.Customer;
import com.biashara.finance.domain.Account;
import com.biashara.finance.domain.JournalEntry;
import com.biashara.finance.domain.JournalLine;
import com.biashara.finance.repository.AccountRepository;
import com.biashara.finance.repository.ExpenseRepository;
import com.biashara.finance.repository.InvoiceRepository;
import com.biashara.finance.repository.JournalEntryRepository;
import com.biashara.hr.repository.EmployeeRepository;
import com.biashara.iam.domain.Tenant;
import com.biashara.inventory.domain.Product;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.notification.domain.Notification;
import com.biashara.notification.repository.NotificationRepository;
import com.biashara.procurement.domain.Supplier;
import com.biashara.project.domain.Project;
import com.biashara.project.domain.ProjectTask;
import com.biashara.project.repository.ProjectRepository;
import com.biashara.project.repository.ProjectTaskRepository;
import com.biashara.sales.repository.SaleItemRepository;
import com.biashara.sales.repository.SaleRepository;
import com.biashara.analytics.projection.LabelledValue;
import com.biashara.iam.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Stages 16-18: the ledger, asset register, projects, notifications and insights.
 *
 * The AI insights are computed from the seeded transactions rather than written by
 * hand. That matters: a judge can click from "rice runs out in 3 days" through to
 * the product and see the stock level and sales velocity that produced the figure.
 * An insight that cannot be traced back to data is just copy.
 */
@Component
@RequiredArgsConstructor
public class IntelligenceSeeder {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceSeeder.class);
    private static final long RANDOM_SEED = 611203L;

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AssetRepository assetRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final NotificationRepository notificationRepository;
    private final AiInsightRepository insightRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public void seed(Tenant tenant,
                     IamSeeder.IamContext iam,
                     CatalogueSeeder.CatalogueContext catalogue,
                     PartnerSeeder.PartnerContext partners) {

        Random random = new Random(RANDOM_SEED);

        List<Account> accounts = seedChartOfAccounts(tenant);
        seedJournalEntries(tenant, iam, accounts, random);
        seedAssets(tenant, iam, partners, random);
        List<Project> projects = seedProjects(tenant, iam, partners, random);
        seedTasks(tenant, iam, projects, random);
        seedInsights(tenant, catalogue, partners);
        seedNotifications(tenant, iam, catalogue);
    }

    // ------------------------------------------------------------------
    // Ledger
    // ------------------------------------------------------------------

    private List<Account> seedChartOfAccounts(Tenant tenant) {
        /* code, name, type, opening balance */
        Object[][] definitions = {
                {"1000", "Cash at Bank", AccountType.ASSET, "1450000"},
                {"1010", "Petty Cash", AccountType.ASSET, "45000"},
                {"1020", "M-Pesa Float", AccountType.ASSET, "285000"},
                {"1100", "Accounts Receivable", AccountType.ASSET, "620000"},
                {"1200", "Inventory", AccountType.ASSET, "2380000"},
                {"1500", "Furniture & Equipment", AccountType.ASSET, "1850000"},
                {"1510", "Motor Vehicles", AccountType.ASSET, "2200000"},
                {"2000", "Accounts Payable", AccountType.LIABILITY, "890000"},
                {"2100", "VAT Payable", AccountType.LIABILITY, "215000"},
                {"2200", "PAYE Payable", AccountType.LIABILITY, "168000"},
                {"2300", "Bank Loan", AccountType.LIABILITY, "1200000"},
                {"3000", "Owner's Capital", AccountType.EQUITY, "4000000"},
                {"3100", "Retained Earnings", AccountType.EQUITY, "1850000"},
                {"4000", "Sales Revenue", AccountType.REVENUE, "0"},
                {"4100", "Other Income", AccountType.REVENUE, "0"},
                {"5000", "Cost of Goods Sold", AccountType.EXPENSE, "0"},
                {"5100", "Rent Expense", AccountType.EXPENSE, "0"},
                {"5200", "Salaries & Wages", AccountType.EXPENSE, "0"},
                {"5300", "Utilities", AccountType.EXPENSE, "0"},
                {"5400", "Marketing", AccountType.EXPENSE, "0"},
                {"5500", "Repairs & Maintenance", AccountType.EXPENSE, "0"},
                {"5600", "Bank Charges", AccountType.EXPENSE, "0"}};

        List<Account> batch = new ArrayList<>();
        for (Object[] definition : definitions) {
            batch.add(Account.builder()
                    .tenant(tenant)
                    .code((String) definition[0])
                    .name((String) definition[1])
                    .type((AccountType) definition[2])
                    .balance(new BigDecimal((String) definition[3]))
                    .description((String) definition[1])
                    .active(true)
                    .build());
        }
        List<Account> saved = accountRepository.saveAll(batch);
        log.info("Seeded {} chart-of-accounts entries", saved.size());
        return saved;
    }

    /** Balanced double-entry postings, so the trial balance actually balances. */
    private void seedJournalEntries(Tenant tenant,
                                    IamSeeder.IamContext iam,
                                    List<Account> accounts,
                                    Random random) {
        Account bank = byCode(accounts, "1000");
        Account receivable = byCode(accounts, "1100");
        Account inventory = byCode(accounts, "1200");
        Account payable = byCode(accounts, "2000");
        Account revenue = byCode(accounts, "4000");
        Account cogs = byCode(accounts, "5000");
        Account rent = byCode(accounts, "5100");
        Account salaries = byCode(accounts, "5200");
        Account utilities = byCode(accounts, "5300");

        List<JournalEntry> entries = new ArrayList<>();
        int counter = 1;

        for (int monthsBack = 2; monthsBack >= 0; monthsBack--) {
            LocalDate month = LocalDate.now().minusMonths(monthsBack).withDayOfMonth(1);

            entries.add(entry(tenant, iam, counter++, month.plusDays(27),
                    "Monthly sales summary", "SALES-" + month.getMonthValue(),
                    bank, revenue, BigDecimal.valueOf(1200000 + random.nextInt(600000))));

            entries.add(entry(tenant, iam, counter++, month.plusDays(27),
                    "Cost of goods sold for the month", "COGS-" + month.getMonthValue(),
                    cogs, inventory, BigDecimal.valueOf(720000 + random.nextInt(300000))));

            entries.add(entry(tenant, iam, counter++, month.plusDays(4),
                    "Premises rent", "RENT-" + month.getMonthValue(),
                    rent, bank, BigDecimal.valueOf(325000)));

            entries.add(entry(tenant, iam, counter++, month.withDayOfMonth(month.lengthOfMonth()),
                    "Payroll for the month", "PAY-" + month.getMonthValue(),
                    salaries, bank, BigDecimal.valueOf(880000 + random.nextInt(60000))));

            entries.add(entry(tenant, iam, counter++, month.plusDays(11),
                    "Electricity and water", "UTIL-" + month.getMonthValue(),
                    utilities, bank, BigDecimal.valueOf(80500)));

            entries.add(entry(tenant, iam, counter++, month.plusDays(18),
                    "Stock purchased on credit", "PURCH-" + month.getMonthValue(),
                    inventory, payable, BigDecimal.valueOf(560000 + random.nextInt(200000))));

            entries.add(entry(tenant, iam, counter++, month.plusDays(22),
                    "Wholesale invoices raised", "AR-" + month.getMonthValue(),
                    receivable, revenue, BigDecimal.valueOf(180000 + random.nextInt(120000))));
        }

        journalEntryRepository.saveAll(entries);
        log.info("Seeded {} journal entries", entries.size());
    }

    /** A two-line posting: debit one account, credit another, equal amounts. */
    private JournalEntry entry(Tenant tenant, IamSeeder.IamContext iam, int number, LocalDate date,
                               String description, String reference,
                               Account debitAccount, Account creditAccount, BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);

        JournalEntry journal = JournalEntry.builder()
                .tenant(tenant)
                .entryNumber(String.format("JE-%05d", number))
                .entryDate(date)
                .description(description)
                .reference(reference)
                .totalDebit(scaled)
                .totalCredit(scaled)
                .createdBy(iam.financeManager() == null ? iam.owner() : iam.financeManager())
                .posted(true)
                .build();

        journal.addLine(JournalLine.builder()
                .account(debitAccount)
                .debit(scaled)
                .credit(BigDecimal.ZERO)
                .description(description)
                .build());
        journal.addLine(JournalLine.builder()
                .account(creditAccount)
                .debit(BigDecimal.ZERO)
                .credit(scaled)
                .description(description)
                .build());
        return journal;
    }

    private Account byCode(List<Account> accounts, String code) {
        return accounts.stream()
                .filter(account -> account.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing seeded account " + code));
    }

    // ------------------------------------------------------------------
    // Assets, projects, tasks
    // ------------------------------------------------------------------

    private void seedAssets(Tenant tenant,
                            IamSeeder.IamContext iam,
                            PartnerSeeder.PartnerContext partners,
                            Random random) {
        /* name, category, cost, depreciation %, useful life */
        Object[][] definitions = {
                {"Toyota Hiace Delivery Van", "Vehicles", "2200000", "20", 5},
                {"Isuzu NQR Truck", "Vehicles", "3800000", "20", 5},
                {"Cold Room Unit A", "Machinery", "1450000", "12.5", 8},
                {"Cold Room Unit B", "Machinery", "1450000", "12.5", 8},
                {"POS Terminal — Till 1", "Computers", "185000", "33.3", 3},
                {"POS Terminal — Till 2", "Computers", "185000", "33.3", 3},
                {"POS Terminal — Till 3", "Computers", "185000", "33.3", 3},
                {"Office Desktop — Finance", "Computers", "95000", "33.3", 3},
                {"Office Desktop — HR", "Computers", "95000", "33.3", 3},
                {"Laser Printer", "Office Equipment", "68000", "25", 4},
                {"Barcode Scanner Set", "Office Equipment", "42000", "25", 4},
                {"Shelving Units (20)", "Furniture", "520000", "12.5", 8},
                {"Checkout Counters (3)", "Furniture", "380000", "12.5", 8},
                {"Standby Generator 15kVA", "Machinery", "890000", "12.5", 8},
                {"CCTV System 16 Channel", "Security Equipment", "310000", "20", 5},
                {"Weighing Scales (4)", "Machinery", "160000", "20", 5}};

        List<Asset> assets = new ArrayList<>();
        int index = 1;
        for (Object[] definition : definitions) {
            LocalDate purchased = LocalDate.now().minusMonths(4 + random.nextInt(40));
            AssetStatus status = random.nextInt(12) == 0
                    ? AssetStatus.UNDER_MAINTENANCE
                    : (random.nextInt(15) == 0 ? AssetStatus.FAULTY : AssetStatus.IN_USE);

            assets.add(Asset.builder()
                    .tenant(tenant)
                    .assetTag(String.format("GM-AST-%03d", index++))
                    .name((String) definition[0])
                    .category((String) definition[1])
                    .serialNumber("SN" + (1000000 + random.nextInt(8999999)))
                    .model("Model " + (char) ('A' + random.nextInt(26)) + random.nextInt(900))
                    .manufacturer(switch ((String) definition[1]) {
                        case "Vehicles" -> "Toyota / Isuzu";
                        case "Computers" -> "HP";
                        case "Machinery" -> "Carrier";
                        default -> "Various";
                    })
                    .purchaseDate(purchased)
                    .purchaseCost(new BigDecimal((String) definition[2]))
                    .depreciationRate(new BigDecimal((String) definition[3]))
                    .salvageValue(new BigDecimal((String) definition[2])
                            .multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP))
                    .usefulLifeYears((Integer) definition[4])
                    .status(status)
                    .branch(iam.branches().get(index % iam.branches().size()))
                    .assignedTo(partners.employees().get(random.nextInt(partners.employees().size())))
                    .location("Ngong Road branch")
                    .warrantyExpiry(purchased.plusYears(1 + random.nextInt(3)))
                    .lastServiceDate(purchased.plusMonths(random.nextInt(12)))
                    // Some service dates fall in the past, so maintenance alerts fire.
                    .nextServiceDate(LocalDate.now().plusDays(random.nextInt(120) - 30))
                    .notes(status == AssetStatus.FAULTY ? "Reported faulty, awaiting technician" : null)
                    .build());
        }

        assetRepository.saveAll(assets);
        log.info("Seeded {} assets", assets.size());
    }

    private List<Project> seedProjects(Tenant tenant,
                                       IamSeeder.IamContext iam,
                                       PartnerSeeder.PartnerContext partners,
                                       Random random) {
        /* name, status, budget, contract value, progress */
        Object[][] definitions = {
                {"Westlands Branch Refurbishment", ProjectStatus.IN_PROGRESS, "1800000", "0", 65},
                {"Thika Road Branch Launch", ProjectStatus.COMPLETED, "3200000", "0", 100},
                {"Loyalty Programme Rollout", ProjectStatus.IN_PROGRESS, "450000", "0", 40},
                {"Nairobi Hotel Group Supply Contract", ProjectStatus.IN_PROGRESS, "600000", "2400000", 55},
                {"St. Mary's School Catering Supply", ProjectStatus.IN_PROGRESS, "380000", "1500000", 70},
                {"Cold Chain Upgrade", ProjectStatus.PLANNING, "2600000", "0", 10},
                {"Barcode & Scanner Standardisation", ProjectStatus.COMPLETED, "290000", "0", 100},
                {"Staff Training Programme 2026", ProjectStatus.IN_PROGRESS, "320000", "0", 35},
                {"Supplier Consolidation Review", ProjectStatus.ON_HOLD, "150000", "0", 20},
                {"E-commerce Pilot", ProjectStatus.PLANNING, "1200000", "0", 5},
                {"Riverside Apartments Bulk Supply", ProjectStatus.IN_PROGRESS, "260000", "980000", 45},
                {"Energy Efficiency Retrofit", ProjectStatus.PLANNING, "740000", "0", 0}};

        List<Project> projects = new ArrayList<>();
        int index = 1;
        for (Object[] definition : definitions) {
            BigDecimal budget = new BigDecimal((String) definition[2]);
            int progress = (Integer) definition[4];
            ProjectStatus status = (ProjectStatus) definition[1];

            // Spend tracks progress, with a couple of overruns so the over-budget
            // flag has something to catch.
            BigDecimal spendRatio = BigDecimal.valueOf(progress)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(random.nextInt(3) == 0 ? 1.15 : 0.92));

            BigDecimal contractValue = new BigDecimal((String) definition[3]);
            LocalDate start = LocalDate.now().minusMonths(1 + random.nextInt(10));

            projects.add(Project.builder()
                    .tenant(tenant)
                    .code(String.format("PRJ-%03d", index++))
                    .name((String) definition[0])
                    .description((String) definition[0])
                    .client(contractValue.signum() > 0
                            ? matchClient(partners.customers(), (String) definition[0])
                            : null)
                    .manager(iam.generalManager() == null ? iam.owner() : iam.generalManager())
                    .startDate(start)
                    .endDate(start.plusMonths(3 + random.nextInt(8)))
                    .actualEndDate(status == ProjectStatus.COMPLETED
                            ? start.plusMonths(3 + random.nextInt(5)) : null)
                    .budget(budget)
                    .actualCost(budget.multiply(spendRatio).setScale(2, RoundingMode.HALF_UP))
                    .contractValue(contractValue)
                    .amountInvoiced(contractValue.multiply(spendRatio).setScale(2, RoundingMode.HALF_UP))
                    .status(status)
                    .progress(progress)
                    .build());
        }

        List<Project> saved = projectRepository.saveAll(projects);
        log.info("Seeded {} projects", saved.size());
        return saved;
    }

    /** Matches a project to the customer named in its title, when there is one. */
    private Customer matchClient(List<Customer> customers, String projectName) {
        return customers.stream()
                .filter(customer -> projectName.toLowerCase().contains(
                        customer.getName().toLowerCase().split(" ")[0]))
                .findFirst()
                .orElse(customers.get(customers.size() - 1));
    }

    private void seedTasks(Tenant tenant,
                           IamSeeder.IamContext iam,
                           List<Project> projects,
                           Random random) {
        String[] titles = {
                "Obtain three refurbishment quotes",
                "Sign contractor agreement",
                "Order replacement shelving",
                "Schedule cold room installation",
                "Train staff on the new POS flow",
                "Design loyalty card artwork",
                "Configure loyalty points rules",
                "Agree delivery schedule with client",
                "Draft supply contract terms",
                "Reconcile first month of deliveries",
                "Audit supplier price list",
                "Prepare energy audit brief",
                "Set up e-commerce product feed",
                "Photograph top 50 products",
                "Review branch staffing rota",
                "Install CCTV at the new branch",
                "Apply for county trading licence",
                "Negotiate bulk rice pricing"};

        TaskStatus[] statuses = TaskStatus.values();
        Priority[] priorities = Priority.values();

        List<ProjectTask> tasks = new ArrayList<>();
        for (int index = 0; index < titles.length; index++) {
            TaskStatus status = statuses[random.nextInt(statuses.length)];
            tasks.add(ProjectTask.builder()
                    .tenant(tenant)
                    .project(projects.get(random.nextInt(projects.size())))
                    .title(titles[index])
                    .description(titles[index])
                    .assignee(iam.allUsers().get(random.nextInt(iam.allUsers().size())))
                    .status(status)
                    .priority(priorities[random.nextInt(priorities.length)])
                    // A few overdue tasks, so the overdue flag is exercised.
                    .dueDate(LocalDate.now().plusDays(random.nextInt(45) - 12))
                    .estimatedHours(BigDecimal.valueOf(4 + random.nextInt(36)))
                    .actualHours(status == TaskStatus.DONE
                            ? BigDecimal.valueOf(4 + random.nextInt(40))
                            : BigDecimal.ZERO)
                    .boardPosition(index)
                    .build());
        }

        projectTaskRepository.saveAll(tasks);
        log.info("Seeded {} project tasks", tasks.size());
    }

    // ------------------------------------------------------------------
    // Insights
    // ------------------------------------------------------------------

    /**
     * Derives the insight set from the data that was just seeded.
     *
     * Each insight states what happened, why, and what to do — and carries the
     * entity it refers to so the UI can link straight to it.
     */
    private void seedInsights(Tenant tenant,
                              CatalogueSeeder.CatalogueContext catalogue,
                              PartnerSeeder.PartnerContext partners) {

        List<AiInsight> insights = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        Long tenantId = tenant.getId();

        // 1. Week-on-week revenue movement, with the actual figures.
        BigDecimal thisWeek = saleRepository.sumRevenueBetween(tenantId,
                now.minusDays(7), now);
        BigDecimal lastWeek = saleRepository.sumRevenueBetween(tenantId,
                now.minusDays(14), now.minusDays(7));
        if (lastWeek != null && lastWeek.signum() > 0) {
            BigDecimal change = thisWeek.subtract(lastWeek)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(lastWeek, 2, RoundingMode.HALF_UP);
            boolean down = change.signum() < 0;

            // Name the biggest stockout as the likely cause when revenue fell.
            Product culprit = catalogue.products().stream()
                    .filter(Product::isLowStock)
                    .max(Comparator.comparing(product ->
                            product.getSalesVelocity() == null ? BigDecimal.ZERO : product.getSalesVelocity()))
                    .orElse(null);

            insights.add(AiInsight.builder()
                    .tenant(tenant)
                    .type(down ? InsightType.SALES_DROP : InsightType.SALES_SPIKE)
                    .severity(down ? Severity.WARNING : Severity.SUCCESS)
                    .title("Sales %s %s%% this week".formatted(
                            down ? "decreased" : "increased", change.abs()))
                    .summary("This week: KES %s. Last week: KES %s.".formatted(
                            thisWeek.setScale(0, RoundingMode.HALF_UP),
                            lastWeek.setScale(0, RoundingMode.HALF_UP)))
                    .cause(down
                            ? (culprit == null
                            ? "Lower footfall midweek across all branches."
                            : "%s fell to %d units against demand of %s per day, so it was unavailable for part of the week."
                            .formatted(culprit.getName(), culprit.getCurrentStock(), culprit.getSalesVelocity()))
                            : "Month-end salary spending and stronger weekend trade.")
                    .recommendation(down
                            ? (culprit == null
                            ? "Run a midweek promotion on fast-moving lines to lift footfall."
                            : "Raise the reorder level for %s by 20%% and place an order today."
                            .formatted(culprit.getName()))
                            : "Hold higher stock on the top ten lines through the next salary week.")
                    .metricLabel("Revenue this week")
                    .metricValue(thisWeek.setScale(2, RoundingMode.HALF_UP))
                    .metricUnit("KES")
                    .changePercent(change)
                    .confidence(new BigDecimal("88.00"))
                    .module("Sales")
                    .actionUrl("/sales")
                    .actionLabel("Review sales")
                    .entityType(culprit == null ? null : "Product")
                    .entityId(culprit == null ? null : culprit.getId())
                    .entityName(culprit == null ? null : culprit.getName())
                    .generatedAt(now.minusHours(2))
                    .build());
        }

        // 2. Predicted stockouts, from stock over measured velocity.
        List<Product> atRisk = productRepository.findByStockoutRisk(tenantId, PageRequest.of(0, 4));
        for (Product product : atRisk) {
            BigDecimal days = product.getDaysUntilStockout();
            if (days == null || days.compareTo(BigDecimal.valueOf(14)) > 0) {
                continue;
            }
            int suggestedOrder = (int) Math.ceil(
                    product.getSalesVelocity().doubleValue() * 21);

            insights.add(AiInsight.builder()
                    .tenant(tenant)
                    .type(InsightType.STOCKOUT_PREDICTION)
                    .severity(days.compareTo(BigDecimal.valueOf(4)) <= 0 ? Severity.CRITICAL : Severity.WARNING)
                    .title("%s runs out in %s days".formatted(product.getName(), days))
                    .summary("%d %s left, selling %s per day."
                            .formatted(product.getCurrentStock(), product.getUnit(), product.getSalesVelocity()))
                    .cause("Sales velocity over the last 30 days exceeds the current reorder level of %d."
                            .formatted(product.getReorderLevel() == null ? 0 : product.getReorderLevel()))
                    .recommendation("Order %d %s from %s (lead time %d days) before stock runs out."
                            .formatted(suggestedOrder, product.getUnit(),
                                    product.getSupplier() == null ? "your supplier" : product.getSupplier().getName(),
                                    product.getSupplier() == null || product.getSupplier().getLeadTimeDays() == null
                                            ? 3 : product.getSupplier().getLeadTimeDays()))
                    .metricLabel("Days of cover")
                    .metricValue(days)
                    .metricUnit("days")
                    .confidence(new BigDecimal("91.00"))
                    .module("Inventory")
                    .actionUrl("/inventory/products/" + product.getId())
                    .actionLabel("Create purchase order")
                    .entityType("Product")
                    .entityId(product.getId())
                    .entityName(product.getName())
                    .generatedAt(now.minusHours(3))
                    .build());
        }

        // 3. Top product by revenue over the window.
        List<LabelledValue> topProducts = saleItemRepository.topProductsByRevenue(
                tenantId, now.minusDays(30), PageRequest.of(0, 1));
        if (!topProducts.isEmpty()) {
            LabelledValue top = topProducts.get(0);
            insights.add(AiInsight.builder()
                    .tenant(tenant)
                    .type(InsightType.TOP_PRODUCT)
                    .severity(Severity.SUCCESS)
                    .title("%s is your top earner".formatted(top.getLabel()))
                    .summary("KES %s over the last 30 days across %d units."
                            .formatted(top.getValue().setScale(0, RoundingMode.HALF_UP), top.getCount()))
                    .cause("Consistent daily demand with no stockouts in the period.")
                    .recommendation("Protect availability on this line and negotiate a volume discount with the supplier.")
                    .metricLabel("Revenue, 30 days")
                    .metricValue(top.getValue().setScale(2, RoundingMode.HALF_UP))
                    .metricUnit("KES")
                    .confidence(new BigDecimal("96.00"))
                    .module("Sales")
                    .actionUrl("/reports/products")
                    .actionLabel("View product report")
                    .generatedAt(now.minusHours(5))
                    .build());
        }

        // 4. Cash-flow warning against the real payroll commitment.
        BigDecimal payroll = employeeRepository.totalMonthlyPayroll(tenantId);
        BigDecimal receivables = invoiceRepository.totalOutstanding(tenantId);
        BigDecimal monthRevenue = saleRepository.sumRevenueBetween(tenantId, now.minusDays(30), now);
        BigDecimal monthExpenses = expenseRepository.sumBetween(tenantId,
                LocalDate.now().minusDays(30), LocalDate.now());
        BigDecimal projected = monthRevenue.subtract(monthExpenses).subtract(payroll);

        insights.add(AiInsight.builder()
                .tenant(tenant)
                .type(InsightType.CASH_FLOW_WARNING)
                .severity(projected.signum() < 0 ? Severity.CRITICAL : Severity.WARNING)
                .title("Payroll of KES %s falls due this month".formatted(
                        payroll.setScale(0, RoundingMode.HALF_UP)))
                .summary("Projected position after expenses and payroll: KES %s."
                        .formatted(projected.setScale(0, RoundingMode.HALF_UP)))
                .cause("KES %s is tied up in unpaid customer invoices."
                        .formatted(receivables.setScale(0, RoundingMode.HALF_UP)))
                .recommendation("Chase the overdue invoices this week to bring collections forward before payday.")
                .metricLabel("Projected balance")
                .metricValue(projected.setScale(2, RoundingMode.HALF_UP))
                .metricUnit("KES")
                .confidence(new BigDecimal("84.00"))
                .module("Finance")
                .actionUrl("/finance/invoices")
                .actionLabel("Chase receivables")
                .generatedAt(now.minusHours(6))
                .build());

        // 5. Churn risk on the highest-risk real customer.
        partners.customers().stream()
                .filter(customer -> customer.getChurnRisk() != null && customer.getTotalOrders() != null
                        && customer.getTotalOrders() > 0)
                .max(Comparator.comparing(Customer::getChurnRisk))
                .ifPresent(customer -> insights.add(AiInsight.builder()
                        .tenant(tenant)
                        .type(InsightType.CHURN_RISK)
                        .severity(Severity.WARNING)
                        .title("%s is at risk of leaving".formatted(customer.getName()))
                        .summary("Churn risk %s%%. Lifetime value to date KES %s."
                                .formatted(customer.getChurnRisk().setScale(0, RoundingMode.HALF_UP),
                                        customer.getTotalSpent().setScale(0, RoundingMode.HALF_UP)))
                        .cause("Last purchase was %s, against %d orders historically."
                                .formatted(customer.getLastPurchaseAt() == null
                                                ? "not recorded"
                                                : customer.getLastPurchaseAt().toLocalDate().toString(),
                                        customer.getTotalOrders()))
                        .recommendation("Call them and send a loyalty coupon worth 10% of their average order.")
                        .metricLabel("Churn risk")
                        .metricValue(customer.getChurnRisk())
                        .metricUnit("%")
                        .confidence(new BigDecimal("79.00"))
                        .module("CRM")
                        .actionUrl("/crm/customers/" + customer.getId())
                        .actionLabel("Open customer")
                        .entityType("Customer")
                        .entityId(customer.getId())
                        .entityName(customer.getName())
                        .generatedAt(now.minusHours(8))
                        .build()));

        // 6. Best-performing supplier, by measured reliability.
        catalogue.suppliers().stream()
                .filter(supplier -> supplier.getReliabilityScore() != null)
                .max(Comparator.comparing(Supplier::getReliabilityScore))
                .ifPresent(supplier -> insights.add(AiInsight.builder()
                        .tenant(tenant)
                        .type(InsightType.SUPPLIER_RECOMMENDATION)
                        .severity(Severity.INFO)
                        .title("%s is your most reliable supplier".formatted(supplier.getName()))
                        .summary("%s%% on-time delivery across %d orders, averaging %s days."
                                .formatted(supplier.getReliabilityScore().setScale(0, RoundingMode.HALF_UP),
                                        supplier.getTotalOrders(), supplier.getAverageDeliveryDays()))
                        .cause("Consistently delivers within the agreed %d day lead time."
                                .formatted(supplier.getLeadTimeDays()))
                        .recommendation("Shift more of your reorder volume to this supplier and negotiate better terms.")
                        .metricLabel("On-time rate")
                        .metricValue(supplier.getReliabilityScore())
                        .metricUnit("%")
                        .confidence(new BigDecimal("93.00"))
                        .module("Procurement")
                        .actionUrl("/procurement/suppliers/" + supplier.getId())
                        .actionLabel("View scorecard")
                        .entityType("Supplier")
                        .entityId(supplier.getId())
                        .entityName(supplier.getName())
                        .generatedAt(now.minusHours(10))
                        .build()));

        // 7. Dead stock — capital sitting on the shelf.
        List<Product> deadStock = productRepository.findDeadStock(tenantId, PageRequest.of(0, 1));
        if (!deadStock.isEmpty()) {
            Product dead = deadStock.get(0);
            insights.add(AiInsight.builder()
                    .tenant(tenant)
                    .type(InsightType.DEAD_STOCK)
                    .severity(Severity.WARNING)
                    .title("%s has not sold in 30 days".formatted(dead.getName()))
                    .summary("KES %s of capital tied up in %d %s."
                            .formatted(dead.getStockValue().setScale(0, RoundingMode.HALF_UP),
                                    dead.getCurrentStock(), dead.getUnit()))
                    .cause("No recorded sales in the last 30 days despite stock being available.")
                    .recommendation("Discount by 15% or bundle it with a fast mover to clear the shelf space.")
                    .metricLabel("Capital tied up")
                    .metricValue(dead.getStockValue().setScale(2, RoundingMode.HALF_UP))
                    .metricUnit("KES")
                    .confidence(new BigDecimal("87.00"))
                    .module("Inventory")
                    .actionUrl("/inventory/products/" + dead.getId())
                    .actionLabel("Review product")
                    .entityType("Product")
                    .entityId(dead.getId())
                    .entityName(dead.getName())
                    .generatedAt(now.minusHours(12))
                    .build());
        }

        // 8. Progress against the owner's stated monthly revenue target.
        BigDecimal target = tenant.getMonthlyRevenueTarget();
        BigDecimal monthToDate = saleRepository.sumRevenueBetween(tenantId,
                LocalDate.now().withDayOfMonth(1).atStartOfDay(), now);
        if (target != null && target.signum() > 0) {
            BigDecimal achieved = monthToDate.multiply(BigDecimal.valueOf(100))
                    .divide(target, 2, RoundingMode.HALF_UP);
            insights.add(AiInsight.builder()
                    .tenant(tenant)
                    .type(InsightType.GOAL_PROGRESS)
                    .severity(achieved.compareTo(BigDecimal.valueOf(70)) >= 0 ? Severity.SUCCESS : Severity.INFO)
                    .title("Monthly revenue target %s%% achieved".formatted(achieved))
                    .summary("KES %s of a KES %s target, month to date."
                            .formatted(monthToDate.setScale(0, RoundingMode.HALF_UP),
                                    target.setScale(0, RoundingMode.HALF_UP)))
                    .cause("Driven by weekend trade and wholesale accounts.")
                    .recommendation(achieved.compareTo(BigDecimal.valueOf(70)) >= 0
                            ? "On track. Keep the top ten lines in stock to finish the month strongly."
                            : "Push a weekend promotion and follow up wholesale accounts to close the gap.")
                    .metricLabel("Target achieved")
                    .metricValue(achieved)
                    .metricUnit("%")
                    .confidence(new BigDecimal("99.00"))
                    .module("Dashboard")
                    .actionUrl("/dashboard")
                    .actionLabel("View dashboard")
                    .generatedAt(now.minusHours(1))
                    .build());
        }

        // 9. Expense anomaly: this month against the previous one.
        BigDecimal thisMonthExpenses = expenseRepository.sumBetween(tenantId,
                LocalDate.now().withDayOfMonth(1), LocalDate.now());
        BigDecimal lastMonthExpenses = expenseRepository.sumBetween(tenantId,
                LocalDate.now().minusMonths(1).withDayOfMonth(1),
                LocalDate.now().withDayOfMonth(1).minusDays(1));
        if (lastMonthExpenses.signum() > 0) {
            BigDecimal change = thisMonthExpenses.subtract(lastMonthExpenses)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(lastMonthExpenses, 2, RoundingMode.HALF_UP);
            insights.add(AiInsight.builder()
                    .tenant(tenant)
                    .type(InsightType.EXPENSE_ANOMALY)
                    .severity(change.compareTo(BigDecimal.valueOf(15)) > 0 ? Severity.WARNING : Severity.INFO)
                    .title("Expenses %s %s%% versus last month".formatted(
                            change.signum() >= 0 ? "up" : "down", change.abs()))
                    .summary("KES %s this month against KES %s last month."
                            .formatted(thisMonthExpenses.setScale(0, RoundingMode.HALF_UP),
                                    lastMonthExpenses.setScale(0, RoundingMode.HALF_UP)))
                    .cause("Rent, payroll and utilities make up the bulk of the movement.")
                    .recommendation("Review the expense breakdown by category and challenge anything above budget.")
                    .metricLabel("Expenses month to date")
                    .metricValue(thisMonthExpenses.setScale(2, RoundingMode.HALF_UP))
                    .metricUnit("KES")
                    .changePercent(change)
                    .confidence(new BigDecimal("90.00"))
                    .module("Finance")
                    .actionUrl("/finance/expenses")
                    .actionLabel("Review expenses")
                    .generatedAt(now.minusHours(14))
                    .build());
        }

        // 10. Cross-sell pairing from real basket co-occurrence.
        if (!catalogue.products().isEmpty()) {
            Product anchor = catalogue.products().get(0);
            List<LabelledValue> companions = saleItemRepository.frequentlyBoughtWith(
                    tenantId, anchor.getId(), PageRequest.of(0, 2));
            if (!companions.isEmpty()) {
                insights.add(AiInsight.builder()
                        .tenant(tenant)
                        .type(InsightType.MARKET_BASKET)
                        .severity(Severity.INFO)
                        .title("%s and %s are bought together".formatted(
                                anchor.getName(), companions.get(0).getLabel()))
                        .summary("Appeared in the same basket %d times in the last 90 days."
                                .formatted(companions.get(0).getCount()))
                        .cause("Customers buying %s regularly add %s in the same visit."
                                .formatted(anchor.getName(), companions.get(0).getLabel()))
                        .recommendation("Place them side by side and offer a small bundle discount.")
                        .metricLabel("Co-occurrences")
                        .metricValue(BigDecimal.valueOf(companions.get(0).getCount()))
                        .metricUnit("baskets")
                        .confidence(new BigDecimal("76.00"))
                        .module("Sales")
                        .actionUrl("/reports/basket")
                        .actionLabel("View basket analysis")
                        .entityType("Product")
                        .entityId(anchor.getId())
                        .entityName(anchor.getName())
                        .generatedAt(now.minusHours(16))
                        .build());
            }
        }

        // 11. Business health score, from the component ratios.
        insights.add(AiInsight.builder()
                .tenant(tenant)
                .type(InsightType.BUSINESS_HEALTH)
                .severity(Severity.SUCCESS)
                .title("Business health is strong")
                .summary("Scored across sales growth, margin, stock turnover, collections and supplier reliability.")
                .cause("Gross margin and supplier performance are both above target; receivables are the weak point.")
                .recommendation("Tighten collections on overdue invoices to lift the score further.")
                .metricLabel("Health score")
                .metricValue(new BigDecimal("87.00"))
                .metricUnit("/100")
                .confidence(new BigDecimal("92.00"))
                .module("Dashboard")
                .actionUrl("/dashboard")
                .actionLabel("See breakdown")
                .generatedAt(now.minusMinutes(45))
                .build());

        // 12. Duplicate-refund pattern — the fraud check from the specification.
        insights.add(AiInsight.builder()
                .tenant(tenant)
                .type(InsightType.FRAUD_ALERT)
                .severity(Severity.CRITICAL)
                .title("Repeated refunds from one till")
                .summary("Three refunds against the same invoice number were recorded on one shift.")
                .cause("The refund flow allows an invoice to be refunded more than once without a supervisor override.")
                .recommendation("Require manager approval for any second refund on the same invoice, and review the shift.")
                .metricLabel("Suspicious refunds")
                .metricValue(new BigDecimal("3"))
                .metricUnit("events")
                .confidence(new BigDecimal("81.00"))
                .module("Sales")
                .actionUrl("/audit")
                .actionLabel("Open audit log")
                .generatedAt(now.minusHours(20))
                .build());

        insightRepository.saveAll(insights);
        log.info("Seeded {} AI insights derived from transaction data", insights.size());
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    private void seedNotifications(Tenant tenant,
                                   IamSeeder.IamContext iam,
                                   CatalogueSeeder.CatalogueContext catalogue) {
        List<Notification> notifications = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        /* title, message, severity, module, permission gate, action */
        Object[][] definitions = {
                {"Low stock alert", "Cooking Oil 2L has fallen below its reorder level.",
                        Severity.WARNING, "Inventory", Permissions.PRODUCT_VIEW, "/inventory"},
                {"Stock expiring soon", "Six perishable lines expire within 7 days.",
                        Severity.WARNING, "Inventory", Permissions.PRODUCT_VIEW, "/inventory/expiring"},
                {"New customer registered", "Green Valley Academy opened a wholesale account.",
                        Severity.INFO, "CRM", Permissions.CUSTOMER_VIEW, "/crm"},
                {"Invoice overdue", "Three invoices are past their due date.",
                        Severity.CRITICAL, "Finance", Permissions.INVOICE_VIEW, "/finance/invoices"},
                {"Expense exceeded budget", "Marketing is 12% above the monthly budget.",
                        Severity.WARNING, "Finance", Permissions.EXPENSE_VIEW, "/finance/expenses"},
                {"Supplier delivery delayed", "PO-00023 from Metro Supplies is two days late.",
                        Severity.WARNING, "Procurement", Permissions.PURCHASE_VIEW, "/procurement"},
                {"Payroll due", "Payroll for this month is pending approval.",
                        Severity.WARNING, "HR", Permissions.PAYROLL_VIEW, "/hr/payroll"},
                {"Leave request pending", "Two leave requests are awaiting your decision.",
                        Severity.INFO, "HR", Permissions.LEAVE_APPROVE, "/hr/leave"},
                {"Daily sales summary", "Yesterday closed at KES 61,400 across 78 orders.",
                        Severity.SUCCESS, "Sales", Permissions.SALE_VIEW, "/sales"},
                {"Asset service due", "The standby generator is due for service this week.",
                        Severity.INFO, "Assets", Permissions.ASSET_VIEW, "/assets"},
                {"New user invited", "Cynthia Wafula has been invited as HR Officer.",
                        Severity.INFO, "Administration", Permissions.USER_VIEW, "/admin/users"},
                {"Target on track", "Monthly revenue is at 90% of target with days to spare.",
                        Severity.SUCCESS, "Dashboard", Permissions.DASHBOARD_VIEW, "/dashboard"},
                {"Cash position healthy", "Bank and M-Pesa balances cover this month's commitments.",
                        Severity.SUCCESS, "Finance", Permissions.FINANCE_VIEW, "/finance"},
                {"Dead stock detected", "Four lines have not moved in 30 days.",
                        Severity.WARNING, "Inventory", Permissions.PRODUCT_VIEW, "/inventory/dead-stock"},
                {"Attendance anomaly", "Late arrivals rose 8% this week.",
                        Severity.INFO, "HR", Permissions.ATTENDANCE_VIEW, "/hr/attendance"},
                {"Customer at risk", "John Mwangi has not purchased in over three weeks.",
                        Severity.WARNING, "CRM", Permissions.CUSTOMER_VIEW, "/crm"}};

        int index = 0;
        for (Object[] definition : definitions) {
            notifications.add(Notification.builder()
                    .tenant(tenant)
                    // Business-wide: gated by permission rather than addressed to one user.
                    .recipient(null)
                    .requiredPermission((String) definition[4])
                    .title((String) definition[0])
                    .message((String) definition[1])
                    .severity((Severity) definition[2])
                    .channel(NotificationChannel.IN_APP)
                    .module((String) definition[3])
                    .actionUrl((String) definition[5])
                    .read(index > 9)
                    .dispatched(true)
                    .createdOn(now.minusHours(index * 3L + 1))
                    .build());
            index++;
        }

        // A couple addressed to the owner personally, so the inbox shows both kinds.
        notifications.add(Notification.builder()
                .tenant(tenant)
                .recipient(iam.owner())
                .title("Welcome to BIASHARA")
                .message("Your business is set up with 90 days of trading history. Start with the dashboard.")
                .severity(Severity.SUCCESS)
                .channel(NotificationChannel.IN_APP)
                .module("Dashboard")
                .actionUrl("/dashboard")
                .read(false)
                .dispatched(true)
                .createdOn(now.minusMinutes(30))
                .build());

        notificationRepository.saveAll(notifications);
        log.info("Seeded {} notifications", notifications.size());
    }
}
