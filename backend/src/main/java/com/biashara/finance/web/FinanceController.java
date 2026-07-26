package com.biashara.finance.web;

import com.biashara.analytics.dto.AnalyticsDtos;
import com.biashara.common.enums.AccountType;
import com.biashara.common.enums.ExpenseStatus;
import com.biashara.common.enums.InvoiceStatus;
import com.biashara.common.enums.PaymentMethod;
import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.NotFoundException;
import com.biashara.finance.domain.Expense;
import com.biashara.finance.dto.FinanceDtos;
import com.biashara.finance.repository.AccountRepository;
import com.biashara.finance.repository.ExpenseRepository;
import com.biashara.finance.repository.InvoiceRepository;
import com.biashara.finance.repository.JournalEntryRepository;
import com.biashara.finance.repository.PaymentRepository;
import com.biashara.iam.repository.DepartmentRepository;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.CurrentUser;
import com.biashara.iam.service.AuditService;
import com.biashara.sales.repository.SaleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Finance", description = "Expenses, invoices, payments, ledger and statements")
public class FinanceController {

    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final SaleRepository saleRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    // --- Expenses -----------------------------------------------------------

    @GetMapping("/expenses")
    @PreAuthorize("hasAuthority('finance.expense.view')")
    @Operation(summary = "Expenses, newest first")
    public Page<FinanceDtos.ExpenseResponse> expenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return expenseRepository
                .findByTenantIdAndDeletedFalseOrderByExpenseDateDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(FinanceDtos.ExpenseResponse::from);
    }

    @PostMapping("/expenses")
    @PreAuthorize("hasAuthority('finance.expense.create')")
    @Operation(summary = "Record an expense")
    @Transactional
    public FinanceDtos.ExpenseResponse createExpense(@Valid @RequestBody FinanceDtos.ExpenseRequest request) {
        Long tenantId = currentUser.tenantId();
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.of("Business", tenantId));

        if (request.expenseDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("An expense cannot be dated in the future");
        }

        long count = expenseRepository.countByTenantIdAndDeletedFalse(tenantId);

        Expense expense = Expense.builder()
                .tenant(tenant)
                .expenseNumber(String.format("EXP-%05d", count + 1))
                .category(request.category())
                .description(request.description())
                .amount(request.amount())
                .taxAmount(BigDecimal.ZERO)
                .expenseDate(request.expenseDate())
                .paymentMethod(request.paymentMethod() == null
                        ? PaymentMethod.CASH
                        : PaymentMethod.valueOf(request.paymentMethod().toUpperCase()))
                .vendor(request.vendor())
                .reference(request.reference())
                // Recorded expenses await approval; they are not self-approving.
                .status(ExpenseStatus.PENDING)
                .costCenter(request.costCenter())
                .recurring(request.recurring())
                .recurrenceInterval(request.recurrenceInterval())
                .notes(request.notes())
                .createdBy(userRepository.findByIdAndDeletedFalse(currentUser.userId()).orElse(null))
                .build();

        if (request.departmentId() != null) {
            departmentRepository.findByIdAndTenantIdAndDeletedFalse(request.departmentId(), tenantId)
                    .ifPresent(expense::setDepartment);
        }

        Expense saved = expenseRepository.save(expense);

        auditService.recordAs(
                userRepository.findByIdAndDeletedFalse(currentUser.userId()).orElse(null),
                tenant, "CREATE_EXPENSE", "Finance", "Expense", saved.getId(),
                saved.getExpenseNumber(),
                "%s — %s %s".formatted(saved.getCategory(), saved.getAmount(), tenant.getCurrency()));

        return FinanceDtos.ExpenseResponse.from(saved);
    }

    @PostMapping("/expenses/{id}/approve")
    @PreAuthorize("hasAuthority('finance.expense.approve')")
    @Operation(summary = "Approve an expense")
    @Transactional
    public FinanceDtos.ExpenseResponse approveExpense(@PathVariable Long id) {
        Expense expense = expenseRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .orElseThrow(() -> NotFoundException.of("Expense", id));

        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new BusinessRuleException("Only a pending expense can be approved");
        }

        var approver = userRepository.findByIdAndDeletedFalse(currentUser.userId()).orElse(null);
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovedBy(approver);
        Expense saved = expenseRepository.save(expense);

        auditService.recordAs(approver, saved.getTenant(), "APPROVE_EXPENSE", "Finance",
                "Expense", saved.getId(), saved.getExpenseNumber(),
                "Approved %s".formatted(saved.getAmount()));

        return FinanceDtos.ExpenseResponse.from(saved);
    }

    @GetMapping("/expenses/breakdown")
    @PreAuthorize("hasAuthority('finance.expense.view')")
    @Operation(summary = "Expense totals by category")
    public List<Map<String, Object>> expenseBreakdown(@RequestParam(defaultValue = "90") int days) {
        return expenseRepository.breakdownByCategory(currentUser.tenantId(),
                        LocalDate.now().minusDays(days), LocalDate.now())
                .stream()
                .map(value -> Map.<String, Object>of(
                        "label", value.getLabel(),
                        "value", value.getValue(),
                        "count", value.getCount()))
                .toList();
    }

    // --- Invoices & payments -------------------------------------------------

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('finance.invoice.view')")
    @Operation(summary = "Invoices, newest first")
    public Page<FinanceDtos.InvoiceResponse> invoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return invoiceRepository
                .findByTenantIdAndDeletedFalseOrderByIssueDateDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(FinanceDtos.InvoiceResponse::from);
    }

    @GetMapping("/invoices/overdue")
    @PreAuthorize("hasAuthority('finance.invoice.view')")
    @Operation(summary = "Invoices past their due date")
    public List<FinanceDtos.InvoiceResponse> overdueInvoices() {
        return invoiceRepository.findOverdue(currentUser.tenantId(), LocalDate.now()).stream()
                .map(FinanceDtos.InvoiceResponse::from)
                .toList();
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('finance.invoice.view')")
    @Operation(summary = "Payments received")
    public Page<FinanceDtos.PaymentResponse> payments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return paymentRepository
                .findByTenantIdAndDeletedFalseOrderByPaidAtDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(FinanceDtos.PaymentResponse::from);
    }

    // --- Ledger --------------------------------------------------------------

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('finance.accounting.view')")
    @Operation(summary = "The chart of accounts")
    public List<FinanceDtos.AccountResponse> accounts() {
        return accountRepository.findByTenantIdAndDeletedFalseOrderByCodeAsc(currentUser.tenantId()).stream()
                .map(FinanceDtos.AccountResponse::from)
                .toList();
    }

    @GetMapping("/journal")
    @PreAuthorize("hasAuthority('finance.accounting.view')")
    @Operation(summary = "Journal entries")
    public Page<FinanceDtos.JournalEntryResponse> journal(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return journalEntryRepository
                .findByTenantIdAndDeletedFalseOrderByEntryDateDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(FinanceDtos.JournalEntryResponse::summary);
    }

    @GetMapping("/journal/{id}")
    @PreAuthorize("hasAuthority('finance.accounting.view')")
    @Operation(summary = "One journal entry with its debit and credit lines")
    public FinanceDtos.JournalEntryResponse journalEntry(@PathVariable Long id) {
        return journalEntryRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .map(FinanceDtos.JournalEntryResponse::detail)
                .orElseThrow(() -> NotFoundException.of("Journal entry", id));
    }

    /** Trial balance by account class — assets should equal liabilities plus equity. */
    @GetMapping("/trial-balance")
    @PreAuthorize("hasAuthority('finance.accounting.view')")
    @Operation(summary = "Balances by account class")
    public Map<String, Object> trialBalance() {
        Long tenantId = currentUser.tenantId();
        BigDecimal assets = accountRepository.sumBalanceByType(tenantId, AccountType.ASSET);
        BigDecimal liabilities = accountRepository.sumBalanceByType(tenantId, AccountType.LIABILITY);
        BigDecimal equity = accountRepository.sumBalanceByType(tenantId, AccountType.EQUITY);

        return Map.of(
                "assets", assets,
                "liabilities", liabilities,
                "equity", equity,
                "revenue", accountRepository.sumBalanceByType(tenantId, AccountType.REVENUE),
                "expenses", accountRepository.sumBalanceByType(tenantId, AccountType.EXPENSE),
                "difference", assets.subtract(liabilities.add(equity)));
    }

    /**
     * Profit and loss for a period.
     *
     * Revenue and cost of goods come from the sales ledger rather than the chart of
     * accounts, so the statement reflects what was actually sold.
     */
    @GetMapping("/profit-and-loss")
    @PreAuthorize("hasAuthority('report.financial')")
    @Operation(summary = "Profit and loss statement")
    public FinanceDtos.ProfitAndLoss profitAndLoss(@RequestParam(defaultValue = "30") int days) {
        Long tenantId = currentUser.tenantId();
        LocalDate from = LocalDate.now().minusDays(days);
        LocalDate to = LocalDate.now();
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toTime = LocalDateTime.now();

        BigDecimal revenue = saleRepository.sumRevenueBetween(tenantId, fromTime, toTime);
        BigDecimal grossProfit = saleRepository.sumGrossProfitBetween(tenantId, fromTime, toTime);
        BigDecimal cogs = revenue.subtract(grossProfit);

        List<AnalyticsDtos.LabelledValue> operating = expenseRepository
                .breakdownByCategory(tenantId, from, to).stream()
                .map(value -> new AnalyticsDtos.LabelledValue(
                        value.getLabel(), value.getValue(), value.getCount()))
                .toList();

        BigDecimal totalOperating = operating.stream()
                .map(AnalyticsDtos.LabelledValue::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfit = grossProfit.subtract(totalOperating);

        return new FinanceDtos.ProfitAndLoss(
                from, to, revenue, cogs, grossProfit,
                percentOf(grossProfit, revenue),
                operating, totalOperating, netProfit,
                percentOf(netProfit, revenue));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('finance.expense.view')")
    @Operation(summary = "Finance headline figures")
    public Map<String, Object> summary() {
        Long tenantId = currentUser.tenantId();
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        return Map.of(
                "monthExpenses", expenseRepository.sumBetween(tenantId, monthStart, LocalDate.now()),
                "pendingExpenses", expenseRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, ExpenseStatus.PENDING),
                "receivables", invoiceRepository.totalOutstanding(tenantId),
                "overdueInvoices", invoiceRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, InvoiceStatus.OVERDUE),
                "collectedThisMonth", paymentRepository.sumBetween(tenantId,
                        monthStart.atStartOfDay(), LocalDateTime.now()),
                "paymentMix", paymentRepository.breakdownByMethod(tenantId,
                                LocalDateTime.now().minusDays(30)).stream()
                        .map(value -> Map.<String, Object>of(
                                "label", value.getLabel(),
                                "value", value.getValue(),
                                "count", value.getCount()))
                        .toList());
    }

    private BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
