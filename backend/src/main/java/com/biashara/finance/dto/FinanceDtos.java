package com.biashara.finance.dto;

import com.biashara.finance.domain.Account;
import com.biashara.finance.domain.Expense;
import com.biashara.finance.domain.Invoice;
import com.biashara.finance.domain.JournalEntry;
import com.biashara.finance.domain.Payment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record ExpenseResponse(
            Long id,
            String expenseNumber,
            String category,
            String description,
            BigDecimal amount,
            LocalDate expenseDate,
            String paymentMethod,
            String vendor,
            String reference,
            String status,
            String department,
            String costCenter,
            String createdBy,
            String approvedBy,
            boolean recurring,
            String recurrenceInterval) {

        public static ExpenseResponse from(Expense expense) {
            return new ExpenseResponse(
                    expense.getId(),
                    expense.getExpenseNumber(),
                    expense.getCategory(),
                    expense.getDescription(),
                    expense.getAmount(),
                    expense.getExpenseDate(),
                    expense.getPaymentMethod() == null ? null : expense.getPaymentMethod().name(),
                    expense.getVendor(),
                    expense.getReference(),
                    expense.getStatus().name(),
                    expense.getDepartment() == null ? null : expense.getDepartment().getName(),
                    expense.getCostCenter(),
                    expense.getCreatedBy() == null ? null : expense.getCreatedBy().getFullName(),
                    expense.getApprovedBy() == null ? null : expense.getApprovedBy().getFullName(),
                    expense.isRecurring(),
                    expense.getRecurrenceInterval());
        }
    }

    public record ExpenseRequest(
            @NotBlank(message = "Category is required") String category,
            @NotBlank(message = "Description is required") String description,
            @NotNull @Positive(message = "Amount must be greater than zero") BigDecimal amount,
            @NotNull(message = "Date is required") LocalDate expenseDate,
            String paymentMethod,
            String vendor,
            String reference,
            Long departmentId,
            String costCenter,
            boolean recurring,
            String recurrenceInterval,
            String notes) {
    }

    public record InvoiceResponse(
            Long id,
            String invoiceNumber,
            Long customerId,
            String customerName,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal amountPaid,
            BigDecimal balance,
            String status,
            boolean overdue,
            long daysOverdue,
            String terms) {

        public static InvoiceResponse from(Invoice invoice) {
            return new InvoiceResponse(
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    invoice.getCustomer() == null ? null : invoice.getCustomer().getId(),
                    invoice.getCustomer() == null ? "Walk-in" : invoice.getCustomer().getName(),
                    invoice.getIssueDate(),
                    invoice.getDueDate(),
                    invoice.getSubtotal(),
                    invoice.getTaxAmount(),
                    invoice.getTotal(),
                    invoice.getAmountPaid(),
                    invoice.getBalance(),
                    invoice.getStatus().name(),
                    invoice.isOverdue(),
                    invoice.getDaysOverdue(),
                    invoice.getTerms());
        }
    }

    public record PaymentResponse(
            Long id,
            String paymentNumber,
            String invoiceNumber,
            String customerName,
            BigDecimal amount,
            String method,
            String status,
            String reference,
            LocalDateTime paidAt,
            String receivedBy) {

        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getInvoice() == null ? null : payment.getInvoice().getInvoiceNumber(),
                    payment.getCustomer() == null ? "Walk-in" : payment.getCustomer().getName(),
                    payment.getAmount(),
                    payment.getMethod().name(),
                    payment.getStatus().name(),
                    payment.getReference(),
                    payment.getPaidAt(),
                    payment.getReceivedBy() == null ? null : payment.getReceivedBy().getFullName());
        }
    }

    public record AccountResponse(
            Long id,
            String code,
            String name,
            String type,
            String subType,
            BigDecimal balance,
            boolean debitNormal,
            String description) {

        public static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getCode(),
                    account.getName(),
                    account.getType().name(),
                    account.getSubType(),
                    account.getBalance(),
                    account.getType().isDebitNormal(),
                    account.getDescription());
        }
    }

    public record JournalLineResponse(
            Long id, String accountCode, String accountName,
            BigDecimal debit, BigDecimal credit, String description) {
    }

    public record JournalEntryResponse(
            Long id,
            String entryNumber,
            LocalDate entryDate,
            String description,
            String reference,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            boolean posted,
            String createdBy,
            List<JournalLineResponse> lines) {

        public static JournalEntryResponse summary(JournalEntry entry) {
            return new JournalEntryResponse(
                    entry.getId(), entry.getEntryNumber(), entry.getEntryDate(),
                    entry.getDescription(), entry.getReference(),
                    entry.getTotalDebit(), entry.getTotalCredit(),
                    entry.isBalanced(), entry.isPosted(),
                    entry.getCreatedBy() == null ? null : entry.getCreatedBy().getFullName(),
                    null);
        }

        public static JournalEntryResponse detail(JournalEntry entry) {
            return new JournalEntryResponse(
                    entry.getId(), entry.getEntryNumber(), entry.getEntryDate(),
                    entry.getDescription(), entry.getReference(),
                    entry.getTotalDebit(), entry.getTotalCredit(),
                    entry.isBalanced(), entry.isPosted(),
                    entry.getCreatedBy() == null ? null : entry.getCreatedBy().getFullName(),
                    entry.getLines().stream()
                            .map(line -> new JournalLineResponse(
                                    line.getId(),
                                    line.getAccount().getCode(),
                                    line.getAccount().getName(),
                                    line.getDebit(),
                                    line.getCredit(),
                                    line.getDescription()))
                            .toList());
        }
    }

    /** A profit and loss statement for a period. */
    public record ProfitAndLoss(
            LocalDate from,
            LocalDate to,
            BigDecimal revenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal grossMarginPercent,
            List<com.biashara.analytics.dto.AnalyticsDtos.LabelledValue> operatingExpenses,
            BigDecimal totalOperatingExpenses,
            BigDecimal netProfit,
            BigDecimal netMarginPercent) {
    }
}
